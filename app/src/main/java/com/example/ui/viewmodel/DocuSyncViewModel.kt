package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.GeminiClient
import com.example.data.api.Part
import com.example.data.api.ResponseSchema
import com.example.data.api.SchemaProperty
import com.example.data.db.AppDatabase
import com.example.data.model.FileUpdate
import com.example.data.model.FileUpdatesResult
import com.example.data.model.NecessityResult
import com.example.data.model.SuggestionItem
import com.example.data.model.SuggestionsResult
import com.example.data.model.SyncProject
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocuSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.syncProjectDao()

    private val moshi: Moshi = GeminiClient.getMoshi()
    private val necessityAdapter = moshi.adapter(NecessityResult::class.java)
    private val suggestionsAdapter = moshi.adapter(SuggestionsResult::class.java)
    private val fileUpdatesAdapter = moshi.adapter(FileUpdatesResult::class.java)

    private val _projects = MutableStateFlow<List<SyncProject>>(emptyList())
    val projects: StateFlow<List<SyncProject>> = _projects.asStateFlow()

    private val _selectedProject = MutableStateFlow<SyncProject?>(null)
    val selectedProject: StateFlow<SyncProject?> = _selectedProject.asStateFlow()

    private val _isRunningPipeline = MutableStateFlow(false)
    val isRunningPipeline: StateFlow<Boolean> = _isRunningPipeline.asStateFlow()

    private val _currentPipelineStep = MutableStateFlow("")
    val currentPipelineStep: StateFlow<String> = _currentPipelineStep.asStateFlow()

    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    private val _apiKeyAvailable = MutableStateFlow(false)
    val apiKeyAvailable: StateFlow<Boolean> = _apiKeyAvailable.asStateFlow()

    fun getParsedSuggestions(project: SyncProject): List<SuggestionItem> {
        if (project.suggestions.isEmpty()) return emptyList()
        return try {
            suggestionsAdapter.fromJson(project.suggestions)?.suggestions ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getParsedFileUpdates(project: SyncProject): List<FileUpdate> {
        if (project.updatedDocs.isEmpty()) return emptyList()
        return try {
            fileUpdatesAdapter.fromJson(project.updatedDocs)?.updates ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    init {
        checkApiKeyAvailability()
        observeProjects()
    }

    private fun checkApiKeyAvailability() {
        val key = BuildConfig.GEMINI_API_KEY
        // Check if the key is default template or empty
        _apiKeyAvailable.value = key.isNotEmpty() &&
                key != "MY_GEMINI_API_KEY" &&
                key != "GEMINI_API_KEY" &&
                !key.contains("PLACEHOLDER")
    }

    private fun observeProjects() {
        viewModelScope.launch {
            dao.getAllProjectsFlow().collectLatest { list ->
                _projects.value = list
                // Prepopulate if database is empty
                if (list.isEmpty()) {
                    prepopulateMockData()
                } else if (_selectedProject.value == null && list.isNotEmpty()) {
                    _selectedProject.value = list.first()
                }
            }
        }
    }

    fun selectProject(project: SyncProject) {
        _selectedProject.value = project
    }

    fun deleteProject(project: SyncProject) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteProject(project)
            if (_selectedProject.value?.id == project.id) {
                _selectedProject.value = _projects.value.firstOrNull { it.id != project.id }
            }
        }
    }

    fun addNewProject(title: String, repo: String, prNumber: Int, diff: String, code: String, doc: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newProj = SyncProject(
                title = title,
                repoName = repo,
                prNumber = prNumber,
                diff = diff,
                codeContext = code,
                docContext = doc,
                status = "Pending"
            )
            val id = dao.insertProject(newProj)
            val savedProj = dao.getProjectById(id)
            if (savedProj != null) {
                withContext(Dispatchers.Main) {
                    _selectedProject.value = savedProj
                }
            }
        }
    }

    /**
     * Executes the absolute full developer pipeline centering three sequential Gemini Pro / Flash 3.5 tasks:
     * 1. Assess Doc Update Necessity (requires_update, rationale)
     * 2. Draft Documentation Change suggestions
     * 3. Apply the changes compiling structured file updates
     */
    fun runDocuSyncPipeline(project: SyncProject) {
        viewModelScope.launch {
            _isRunningPipeline.value = true
            _apiError.value = null
            
            // Temporary variable to track state modifications
            var currentProj = project
            
            try {
                // STEP 1: Assessment of Necessity
                _currentPipelineStep.value = "Phase 1: Assessing Documentation Necessity..."
                updateProjectStatus(currentProj, "Analyzing")
                delay(1000)

                val necessityResult = if (_apiKeyAvailable.value) {
                    executeNecessityCheckWithGemini(currentProj)
                } else {
                    simulateNecessityCheck(currentProj)
                }
                
                currentProj = currentProj.copy(
                    status = "NecessityAnalyzed",
                    requiresUpdate = necessityResult.requires_update,
                    necessityReasoning = necessityResult.rationale
                )
                dao.updateProject(currentProj)
                _selectedProject.value = currentProj
                delay(1500)

                if (!necessityResult.requires_update) {
                    // Update complete - No documentation changes wanted!
                    currentProj = currentProj.copy(status = "Done")
                    dao.updateProject(currentProj)
                    _selectedProject.value = currentProj
                    _isRunningPipeline.value = false
                    return@launch
                }

                // STEP 2: Draft Suggested Changes
                _currentPipelineStep.value = "Phase 2: Generating Change Suggestions..."
                delay(1200)

                val suggestionsResult = if (_apiKeyAvailable.value) {
                    executeSuggestionsDraftWithGemini(currentProj)
                } else {
                    simulateSuggestionsDraft(currentProj)
                }

                val suggestionsJson = suggestionsAdapter.toJson(suggestionsResult)
                currentProj = currentProj.copy(
                    status = "SuggestionsDrafted",
                    suggestions = suggestionsJson
                )
                dao.updateProject(currentProj)
                _selectedProject.value = currentProj
                delay(1500)

                // STEP 3: Generate File Update Code
                _currentPipelineStep.value = "Phase 3: Synthesizing Updated Documentation..."
                delay(1500)

                val updatesResult = if (_apiKeyAvailable.value) {
                    executeFileUpdatesWithGemini(currentProj, suggestionsResult.suggestions)
                } else {
                    simulateFileUpdates(currentProj)
                }

                val updatesJson = fileUpdatesAdapter.toJson(updatesResult)
                currentProj = currentProj.copy(
                    status = "Completed",
                    updatedDocs = updatesJson
                )
                dao.updateProject(currentProj)
                _selectedProject.value = currentProj
                delay(1000)
                
            } catch (e: Exception) {
                _apiError.value = "Pipeline Failed: ${e.message ?: "Unknown Error"}"
                currentProj = currentProj.copy(status = "Pending")
                dao.updateProject(currentProj)
                _selectedProject.value = currentProj
            } finally {
                _isRunningPipeline.value = false
                _currentPipelineStep.value = ""
            }
        }
    }

    fun updateProjectDocContent(project: SyncProject, filename: String, newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileUpdates = getParsedFileUpdates(project)
            val updatedList = fileUpdates.map {
                if (it.filename == filename) it.copy(content = newContent) else it
            }
            val updatesResult = FileUpdatesResult(updatedList)
            val updatesJson = fileUpdatesAdapter.toJson(updatesResult)
            val updatedProj = project.copy(updatedDocs = updatesJson)
            dao.updateProject(updatedProj)
            withContext(Dispatchers.Main) {
                _selectedProject.value = updatedProj
            }
        }
    }

    fun runDocuSyncRefinement(project: SyncProject, filename: String, instruction: String) {
        viewModelScope.launch {
            _isRunningPipeline.value = true
            _apiError.value = null
            _currentPipelineStep.value = "Refining documentation with instructions"
            delay(1000)

            try {
                val fileUpdates = getParsedFileUpdates(project)
                val targetUpdate = fileUpdates.find { it.filename == filename } ?: throw Exception("File not found in sync set.")
                
                val newContent = if (_apiKeyAvailable.value) {
                    executeRefinementWithGemini(project, targetUpdate.content, instruction)
                } else {
                    simulateRefinement(targetUpdate.content, instruction)
                }

                val updatedList = fileUpdates.map {
                    if (it.filename == filename) it.copy(content = newContent) else it
                }
                val updatesResult = FileUpdatesResult(updatedList)
                val updatesJson = fileUpdatesAdapter.toJson(updatesResult)
                
                val updatedProj = project.copy(
                    updatedDocs = updatesJson,
                    status = "Completed"
                )
                dao.updateProject(updatedProj)
                _selectedProject.value = updatedProj
                delay(1000)
            } catch (e: Exception) {
                _apiError.value = "Refinement Failed: ${e.message ?: "Unknown Error"}"
            } finally {
                _isRunningPipeline.value = false
                _currentPipelineStep.value = ""
            }
        }
    }

    private suspend fun executeRefinementWithGemini(proj: SyncProject, originalContent: String, instruction: String): String = withContext(Dispatchers.IO) {
        val systemPrompt = "You are an expert technical writer. Tweak the provided documentation file based on specific user refinement instructions, returning ONLY the updated file's complete new markdown content."
        val prompt = """
            Please read the original documentation, the associated Git Diff, and the User's Custom refinement instruction. Rewrite the documentation file, incorporating the user's specific feedback.
            Return ONLY the raw markdown content without any surrounding JSON structures or markdown code block backtick wrapper (except for actual snippets within the documentation itself).
            
            [GIT DIFF]
            ${proj.diff}
            
            [ORIGINAL MARKDOWN]
            $originalContent
            
            [USER REFINEMENT INSTRUCTION]
            $instruction
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.2
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        val response = GeminiClient.apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
        var text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response received from Gemini for refinement.")
        
        // Clean markdown backticks wrapper if LLM returned it
        if (text.startsWith("```markdown", true)) {
            text = text.substringAfter("```markdown")
            if (text.endsWith("```")) {
                text = text.substringBeforeLast("```")
            }
        } else if (text.startsWith("```")) {
            text = text.substringAfter("```")
            if (text.endsWith("```")) {
                text = text.substringBeforeLast("```")
            }
        }
        text.trim()
    }

    private fun simulateRefinement(originalContent: String, instruction: String): String {
        return """
            $originalContent
            
            ---
            ### 📝 Developer Updates: Incorporating Feedback
            
            * **Feedback Addressed**: "$instruction"
            * **Tweak Applied**: Successfully appended specialized configurations and developer notes matching the requested specifications.
        """.trimIndent()
    }

    private suspend fun updateProjectStatus(proj: SyncProject, status: String) {
        val updated = proj.copy(status = status)
        dao.updateProject(updated)
        _selectedProject.value = updated
    }

    // --- GEMINI REAL CALLS ---

    private suspend fun executeNecessityCheckWithGemini(proj: SyncProject): NecessityResult = withContext(Dispatchers.IO) {
        val systemPrompt = "You are a professional software documentation sync manager. Evaluate whether the provided git diff requires updating the existing associated documentation context."
        val prompt = """
            Evaluate the following Git Diff against the Code Context and existing Documentation Context:
            
            [GIT DIFF]
            ${proj.diff}
            
            [CODE CONTEXT]
            ${proj.codeContext}
            
            [DOCUMENTATION CONTEXT]
            ${proj.docContext}
            
            Respond strictly in valid JSON format according to this schema:
            {
              "requires_update": true or false,
              "rationale": "An explanation detailing exactly why or why not documentation updates are necessary based on modified methods, interfaces, configs or signatures in the Diff"
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.1,
                responseMimeType = "application/json",
                responseSchema = ResponseSchema(
                    type = "OBJECT",
                    properties = mapOf(
                        "requires_update" to SchemaProperty(type = "BOOLEAN", description = "True if documentation updating is necessary"),
                        "rationale" to SchemaProperty(type = "STRING", description = "Reasoning statement")
                    ),
                    required = listOf("requires_update", "rationale")
                )
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        val response = GeminiClient.apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response received from Gemini.")

        necessityAdapter.fromJson(text) ?: throw Exception("Failed to parse evaluation response JSON.")
    }

    private suspend fun executeSuggestionsDraftWithGemini(proj: SyncProject): SuggestionsResult = withContext(Dispatchers.IO) {
        val systemPrompt = "You are a software documentation engineer drafting specific bullet-point list items describing documentation changes required for a project."
        val prompt = """
            Draft custom documentation suggestions for the Git Diff below.
            Identify exactly which markdown files should be updated and details of what information to write.
            
            [GIT DIFF]
            ${proj.diff}
            
            [EXISTING DOCS CONTEXT]
            ${proj.docContext}
            
            [RATIONALE]
            ${proj.necessityReasoning}
            
            Respond strictly in valid JSON format matching this schema:
            {
              "suggestions": [
                {
                  "description": "Specific action item description of the changes required",
                  "affected_file": "name of target markdown document file to modify (e.g., docs/PAYMENTS.md)"
                }
              ]
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.2,
                responseMimeType = "application/json",
                responseSchema = ResponseSchema(
                    type = "OBJECT",
                    properties = mapOf(
                        "suggestions" to SchemaProperty(
                            type = "ARRAY",
                            items = SchemaProperty(
                                type = "OBJECT",
                                properties = mapOf(
                                    "description" to SchemaProperty(type = "STRING"),
                                    "affected_file" to SchemaProperty(type = "STRING")
                                ),
                                required = listOf("description", "affected_file")
                            )
                        )
                    ),
                    required = listOf("suggestions")
                )
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        val response = GeminiClient.apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response received from Gemini for suggestions.")

        suggestionsAdapter.fromJson(text) ?: throw Exception("Failed to parse suggestions response JSON.")
    }

    private suspend fun executeFileUpdatesWithGemini(proj: SyncProject, suggestions: List<SuggestionItem>): FileUpdatesResult = withContext(Dispatchers.IO) {
        val systemPrompt = "You are an automated code generator. Update documentation files by incorporating suggestions and returning complete new written markdown files."
        
        val suggestionsStr = suggestions.joinToString("\n") { "- Modifying ${it.affected_file}: ${it.description}" }
        val prompt = """
            Please read the suggestions and rewrite the relevant markdown files. Return the FULL markdown file content.
            
            [SUGGESTIONS]
            $suggestionsStr
            
            [GIT DIFF]
            ${proj.diff}
            
            [ORIGINAL CODE CONTEXT]
            ${proj.codeContext}
            
            [ORIGINAL DOCUMENTATION]
            ${proj.docContext}
            
            Respond strictly in valid JSON format matching this schema:
            {
              "updates": [
                {
                  "filename": "name of markdown file (e.g., docs/PAYMENTS.md)",
                  "content": "Entirely complete, fully rendered, formatted Markdown content of the document including headers, tables, API endpoints, etc. incorporating all updates."
                }
              ]
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.1,
                responseMimeType = "application/json",
                responseSchema = ResponseSchema(
                    type = "OBJECT",
                    properties = mapOf(
                        "updates" to SchemaProperty(
                            type = "ARRAY",
                            items = SchemaProperty(
                                type = "OBJECT",
                                properties = mapOf(
                                    "filename" to SchemaProperty(type = "STRING"),
                                    "content" to SchemaProperty(type = "STRING")
                                ),
                                required = listOf("filename", "content")
                            )
                        )
                    ),
                    required = listOf("updates")
                )
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        val response = GeminiClient.apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response received from Gemini for documents update.")

        fileUpdatesAdapter.fromJson(text) ?: throw Exception("Failed to parse file updates response JSON.")
    }

    // --- BLUEPRINT SIMULATION GENERATION ---

    private fun simulateNecessityCheck(proj: SyncProject): NecessityResult {
        return when {
            proj.title.contains("Stripe", true) -> NecessityResult(
                requires_update = true,
                rationale = "The Git Diff introduces dynamic Google Pay and Apple Pay token support in the payments pipeline. Additionally, payment gateway parameters in PaymentService constructor were updated from single apiKey to structured PaymentConfig context class, which strictly breaks original static API integration documentation in PAYMENTS.md."
            )
            proj.title.contains("Biometric", true) -> NecessityResult(
                requires_update = true,
                rationale = "A completely new authentication factor (Biometrics via fingerprint/face recognition scanner) has been integrated. The current SECURITY.md framework only details alphanumeric PIN mechanics and lacks setup guidelines, SDK hooks, and user prompt config parameters."
            )
            proj.title.contains("Cache", true) -> NecessityResult(
                requires_update = true,
                rationale = "Memory cache architecture transitioned from standard in-memory ConcurrentHashMap to an Android content-aware LruCache with automatic limit evictions. The PERFORMANCE.md needs adjustments to reflect updated eviction sizes and constructor configurations to guide mobile resource constraints."
            )
            else -> NecessityResult(
                requires_update = true,
                rationale = "Custom Developer Diff detected. The code changes alter function signatures, system parameters, or core class structures. To avoid architectural misalignment and broken integration points, standard documentation must be synced with current implementations."
            )
        }
    }

    private fun simulateSuggestionsDraft(proj: SyncProject): SuggestionsResult {
        val list = when {
            proj.title.contains("Stripe", true) -> listOf(
                SuggestionItem(
                    description = "Update Authentication configuration sections to include dynamic JWT configuration parameters and key structures.",
                    affected_file = "docs/PAYMENTS.md"
                ),
                SuggestionItem(
                    description = "Document the newly introduced dynamic Google Pay payment controller endpoints and callback mappings.",
                    affected_file = "docs/PAYMENTS.md"
                )
            )
            proj.title.contains("Biometric", true) -> listOf(
                SuggestionItem(
                    description = "Add installation guidelines and dependencies for Android Biometrics API.",
                    affected_file = "docs/SECURITY.md"
                ),
                SuggestionItem(
                    description = "Add sample code to describe callbacks: onAuthenticationSucceeded, onAuthenticationFailed, and fatal security triggers.",
                    affected_file = "docs/SECURITY.md"
                )
            )
            proj.title.contains("Cache", true) -> listOf(
                SuggestionItem(
                    description = "Explain LRU automatic size tracking rules and how developers should config maximum cache limits.",
                    affected_file = "docs/PERFORMANCE.md"
                ),
                SuggestionItem(
                    description = "Highlight deprecation of the old clearCachedImages() synchronous method in favor of asynchronous clearAllSync().",
                    affected_file = "docs/PERFORMANCE.md"
                )
            )
            else -> listOf(
                SuggestionItem(
                    description = "Update developer guidelines to document modified API endpoints, parameter maps, and updated setup structures shown in git diff.",
                    affected_file = "docs/INDEX.md"
                )
            )
        }
        return SuggestionsResult(list)
    }

    private fun simulateFileUpdates(proj: SyncProject): FileUpdatesResult {
        val updates = when {
            proj.title.contains("Stripe", true) -> listOf(
                FileUpdate(
                    filename = "docs/PAYMENTS.md",
                    content = """
                        # Payments Integration Guide

                        Welcome to the official Payments Service SDK documentation. The active SDK integrates with Stripe and dynamic checkout options to securely process user transactions.

                        ## Setup & Initialization

                        The setup configuration now requires initializing a `PaymentConfig` rather than passing raw keys, providing stronger security scope and automatic fallback.

                        ```kotlin
                        // Initialize Payment Config containing API scopes and branding context
                        val config = PaymentConfig(
                            publicKey = "pk_live_...",
                            merchantName = "DocuSync Store",
                            supportGooglePay = true,
                            supportApplePay = true
                        )
                        PaymentService.initialize(context, config)
                        ```

                        ## API Endpoint Schemas

                        ### 1. Unified Charge Controller
                        
                        * **Request Method**: `POST`
                        * **Endpoint Path**: `/api/v2/payments/charge`
                        * **Content-Type**: `application/json`

                        | Parameter Name | Data Type | Description | Required |
                        | :--- | :--- | :--- | :--- |
                        | `amount_cents` | Long | Total transaction amount in cents | **Yes** |
                        | `currency` | String | ISO-4217 Currency Code (e.g. "USD") | **Yes** |
                        | `token_id` | String | Dynamic secure Stripe checkout token | Defaults to null |
                        | `pay_type` | String | Either `STRIPE`, `GOOGLE_PAY`, `APPLE_PAY` | **Yes** |

                        ---

                        ## Dynamic Google Pay Token Callbacks

                        The newly integrated callback handlers allow listening to native wallet actions. Implement `PaymentResultCallback` in your parent Activity:

                        ```kotlin
                        PaymentService.setResultListener(object : PaymentResultCallback {
                            override fun onWalletAuthorizeSuccess(transactionId: String) {
                                showStatus("Transaction approved: ${'$'}transactionId")
                            }
                            override fun onWalletAuthorizeFail(errorCode: Int, message: String) {
                                showError("Authorize error (${'$'}errorCode): ${'$'}message")
                            }
                        })
                        ```

                        ---
                        *Document generated & synced automatically via DocuSync by Google AI Studio.*
                    """.trimIndent()
                )
            )
            proj.title.contains("Biometric", true) -> listOf(
                FileUpdate(
                    filename = "docs/SECURITY.md",
                    content = """
                        # Security Framework & Authentication Models

                        To comply with corporate and personal banking app security standards, our framework implements multi-factor credential authentication scopes on Android devices.

                        ## Android Biometric Setup

                        Biometric authentication utilizes the underlying Android Keystore hardware sandbox to verify fingerprint or facial properties securely.

                        ### Gradle Dependencies

                        Validate that the hardware biometric dependency exists in your `app/build.gradle.kts`:

                        ```kotlin
                        dependencies {
                            implementation("androidx.biometric:biometric-ktx:1.4.0")
                        }
                        ```

                        ## Sample Integration Flow

                        Run the authentication prompt utilizing the simple `BiometricsHelper` class.

                        ```kotlin
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Verify Identity")
                            .setSubtitle("Confirm Fingerprint validation pattern to unlock secure vault")
                            .setNegativeButtonText("Use Passcode")
                            .build()

                        BiometricsHelper.authenticate(
                            activity = this,
                            promptInfo = promptInfo,
                            onSuccess = { crypto ->
                                accessSecureData(crypto)
                            },
                            onFailure = { errorCode, errorText ->
                                handleSecurityLockout(errorCode, errorText)
                            }
                        )
                        ```

                        ## Error Code References

                        | Error Name | Code ID | Action Required |
                        | :--- | :--- | :--- |
                        | `BIOMETRIC_ERROR_LOCKOUT` | `7` | Prevent attempts for 30 seconds |
                        | `BIOMETRIC_ERROR_HW_UNAVAILABLE` | `1` | Fallback dynamically to credential PIN input screen |
                        | `BIOMETRIC_ERROR_NO_BIOMETRICS` | `11` | Guide developer to device Settings to enroll fingerprints |

                        ---
                        *Document generated & synced automatically via DocuSync by Google AI Studio.*
                    """.trimIndent()
                )
            )
            proj.title.contains("Cache", true) -> listOf(
                FileUpdate(
                    filename = "docs/PERFORMANCE.md",
                    content = """
                        # Image Memory Cache & Performance Scaling

                        This guide highlights optimization constraints for image loading and object layouts to prevent memory leaks and Garbage Collection (GC) thrashing on lower-end mobile devices.

                        ## Dynamic LRU Cache Architecture

                        Instead of unbounded Map caches, the system uses an Android resource-aware `LruCache` tracking bitmap byte sizes dynamically.

                        ```kotlin
                        // Initialize image buffer cache claiming 25% of available application RAM
                        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
                        val cacheSizeKb = maxMemoryKb / 4

                        val imageCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
                            override fun sizeOf(key: String, value: Bitmap): Int {
                                return value.byteCount / 1024 // Return sizes in Kilobytes
                            }
                        }
                        ```

                        ### Eviction Mechanism

                        When cache contents overshoot limits, Least Recently Used item indices are deleted automatically.

                        ### Deprecation Warnings

                        * ⚠️ `ImageLoader.clearCachedImages()` is **deprecated**. It ran synchronously, halting UI frame renders.
                        * ✅ Use `ImageLoader.clearAllAsync(onComplete: () -> Unit)` to flush memory heaps asynchronously inside background threads.

                        ---
                        *Document generated & synced automatically via DocuSync by Google AI Studio.*
                    """.trimIndent()
                )
            )
            else -> listOf(
                FileUpdate(
                    filename = "docs/INDEX.md",
                    content = """
                        # Documentation Index

                        General synchronization log of custom user-loaded codebase updates.

                        ## Latest Synchronized Changes

                        The system has detected that signatures or classes in the repository have changed. Ensure all API signatures conform to specifications.

                        * Change Description: User Custom Git Diff
                        * System Status: Active
                        * Updated Content: Successfully parsed and evaluated

                        ---
                        *Document generated & synced automatically via DocuSync by Google AI Studio.*
                    """.trimIndent()
                )
            )
        }
        return FileUpdatesResult(updates)
    }

    private suspend fun prepopulateMockData() {
        withContext(Dispatchers.IO) {
            val mockProjects = listOf(
                SyncProject(
                    title = "Stripe Wallet & Checkout controller integration",
                    repoName = "docusync/payments-api",
                    prNumber = 204,
                    diff = """
                        diff --git a/src/main/java/com/stripe/PaymentService.kt b/src/main/java/com/stripe/PaymentService.kt
                        index a529ee..f229bb 100644
                        --- a/src/main/java/com/stripe/PaymentService.kt
                        +++ b/src/main/java/com/stripe/PaymentService.kt
                        @@ -12,5 +12,14 @@
                         class PaymentService {
                        -    fun initialize(apiKey: String) {
                        -        this.keys = apiKey
                        +    fun initialize(context: Context, config: PaymentConfig) {
                        +        this.keys = config.publicKey
                        +        this.merchantName = config.merchantName
                        +        this.googlePayActive = config.supportGooglePay
                        +        this.applePayActive = config.supportApplePay
                             }
                        +
                        +    fun chargeUnified(amountCents: Long, currency: String, payType: String, tokenId: String? = null) {
                        +         val payload = jsonBodyOf("amount" to amountCents, "currency" to currency, "type" to payType, "token" to tokenId)
                        +         client.post("/v2/payments/charge", payload)
                        +    }
                         }
                    """.trimIndent(),
                    codeContext = """
                        package com.stripe
                        
                        import android.content.Context
                        
                        data class PaymentConfig(
                            val publicKey: String,
                            val merchantName: String,
                            val supportGooglePay: Boolean,
                            val supportApplePay: Boolean
                        )
                        
                        object PaymentService {
                            private var keys: String? = null
                            private var merchantName: String? = null
                            private var googlePayActive: Boolean = false
                            private var applePayActive: Boolean = false
                            
                            fun initialize(context: Context, config: PaymentConfig) { ... }
                            fun chargeUnified(...) { ... }
                        }
                    """.trimIndent(),
                    docContext = """
                        # Payments Integration Guide
                        
                        ## Setup
                        To use payments, call initialize:
                        `PaymentService.initialize("YOUR_STRIPE_API_KEY")`
                        
                        ## API Endpoint
                        Unified endpoint: None. Use raw cards only.
                    """.trimIndent(),
                    status = "Pending"
                ),
                SyncProject(
                    title = "Biometrics unlock hardware integration flow",
                    repoName = "docusync/auth-security",
                    prNumber = 88,
                    diff = """
                        diff --git a/src/main/java/com/auth/BiometricsHelper.kt b/src/main/java/com/auth/BiometricsHelper.kt
                        new file mode 100644
                        index 000000..77ae34
                        --- /dev/null
                        +++ b/src/main/java/com/auth/BiometricsHelper.kt
                        @@ -0,0 +1,15 @@
                        +package com.auth
                        +import androidx.biometric.BiometricPrompt
                        +
                        +object BiometricsHelper {
                        +    fun authenticate(activity: FragmentActivity, promptInfo: BiometricPrompt.PromptInfo, onSuccess: (BiometricPrompt.CryptoObject?) -> Unit, onFailure: (Int, CharSequence) -> Unit) {
                        +        val executor = ContextCompat.getMainExecutor(activity)
                        +        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                        +             override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        +                 onSuccess(result.cryptoObject)
                        +             }
                        +        })
                        +        prompt.authenticate(promptInfo)
                        +    }
                        +}
                    """.trimIndent(),
                    codeContext = "Autogenerated fingerprint scanning helpers using standard native androidx.biometric stack modules.",
                    docContext = "# Security Framework & Authentication Models\n\nCurrently, the app only supports logging in with a 6-digit PIN numerical keypad screen.",
                    status = "Pending"
                ),
                SyncProject(
                    title = "Optimize memory bitmaps with LRU caching mechanism",
                    repoName = "docusync/media-cache",
                    prNumber = 412,
                    diff = """
                        diff --git a/media/ImageLoader.kt b/media/ImageLoader.kt
                        index a31a0..c3529 100644
                        --- a/media/ImageLoader.kt
                        +++ b/media/ImageLoader.kt
                        @@ -4,6 +4,11 @@
                         object ImageLoader {
                        -    private val rawCache = HashMap<String, Bitmap>() // Unbounded leaks
                        +    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
                        +    private val cacheSizeKb = maxMemoryKb / 4
                        +    private val lruCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
                        +         override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
                        +    }
                        +    
                        +    @Deprecated("Runs on UI thread and fails synchronously", ReplaceWith("clearAllAsync()"))
                             fun clearCachedImages() {
                        -        rawCache.clear()
                        +        lruCache.evictAll()
                             }
                        +    
                        +    fun clearAllAsync(onComplete: () -> Unit) {
                        +         CoroutineScope(Dispatchers.IO).launch {
                        +              lruCache.evictAll()
                        +              withContext(Dispatchers.Main) { onComplete() }
                        +         }
                        +    }
                         }
                    """.trimIndent(),
                    codeContext = "Image loading library containing caching mechanisms with eviction configurations.",
                    docContext = "# Image Memory Cache\n\nGuidelines for freeing up space. Run `ImageLoader.clearCachedImages()` to empty memory cache immediately.",
                    status = "Pending"
                )
            )

            for (p in mockProjects) {
                dao.insertProject(p)
            }
        }
    }
}

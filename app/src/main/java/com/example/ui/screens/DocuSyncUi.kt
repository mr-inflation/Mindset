package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SyncProject
import com.example.ui.viewmodel.DocuSyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocuSyncUi(viewModel: DocuSyncViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val selectedProj by viewModel.selectedProject.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunningPipeline.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentPipelineStep.collectAsStateWithLifecycle()
    val apiError by viewModel.apiError.collectAsStateWithLifecycle()
    val apiKeyAvailable by viewModel.apiKeyAvailable.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var expandedProjectDrawer by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("dashboard") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 800.dp

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Sync AI Logo",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "DocSync AI",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "GEMINI 1.5 PRO ENGINE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        actions = {
                            // Key Status display
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (apiKeyAvailable) Color(0xFF10B981).copy(alpha = 0.15f)
                                        else Color(0xFFF59E0B).copy(alpha = 0.15f)
                                    )
                                    .border(
                                        1.dp,
                                        if (apiKeyAvailable) Color(0xFF10B981) else Color(0xFFF59E0B),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (apiKeyAvailable) Color(0xFF10B981) else Color(0xFFF59E0B))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (apiKeyAvailable) "Gemini Live" else "Preview Simulation",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (apiKeyAvailable) Color(0xFF10B981) else Color(0xFFF59E0B)
                                    )
                                }
                            }

                            if (!isWideScreen) {
                                IconButton(onClick = { expandedProjectDrawer = !expandedProjectDrawer }) {
                                    Icon(
                                        imageVector = if (expandedProjectDrawer) Icons.Default.Close else Icons.Default.History,
                                        contentDescription = "Project History"
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.testTag("add_project_button")
                            ) {
                                Icon(imageVector = Icons.Default.Code, contentDescription = "New Sync Task")
                            }
                        }
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            floatingActionButton = {
                selectedProj?.let { proj ->
                    if (proj.status != "Analyzing" && !isRunning) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.runDocuSyncPipeline(proj) },
                            icon = { Icon(Icons.Default.FlashOn, "Run Pipeline") },
                            text = { Text("Generate Doc Sync") },
                            modifier = Modifier
                                .testTag("run_pipeline_fab")
                                .padding(bottom = 16.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Wide Screen: Persistent Left History Drawer
                if (isWideScreen) {
                    ProjectHistorySidebar(
                        projects = projects,
                        selectedProj = selectedProj,
                        currentScreen = currentScreen,
                        onSelectDashboard = { currentScreen = "dashboard" },
                        onSelect = {
                            viewModel.selectProject(it)
                            currentScreen = "workbench"
                        },
                        onDelete = { viewModel.deleteProject(it) },
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }

                // Main Workspace Contents
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (projects.isEmpty()) {
                        EmptyWorkspaceWidget { showAddDialog = true }
                    } else if (currentScreen == "dashboard") {
                        DashboardView(
                            projects = projects,
                            onSelectProject = {
                                viewModel.selectProject(it)
                                currentScreen = "workbench"
                            },
                            onDeleteProject = { viewModel.deleteProject(it) },
                            onCreateRequested = { showAddDialog = true }
                        )
                    } else if (selectedProj != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Back / Breadcrumb Nav Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { currentScreen = "dashboard" }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to Dashboard",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Back to PR Sync Dashboard",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ProjectWorkbench(
                                project = selectedProj!!,
                                viewModel = viewModel,
                                apiKeyAvailable = apiKeyAvailable,
                                apiError = apiError
                            )
                        }
                    }
                }
            }

            // Compact screen: Temporary history overlay drawer
            if (!isWideScreen && expandedProjectDrawer) {
                Dialog(onDismissRequest = { expandedProjectDrawer = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Project Execution Log",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                IconButton(onClick = { expandedProjectDrawer = false }) {
                                    Icon(Icons.Default.Close, "Dismiss")
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))
                            ProjectHistorySidebar(
                                projects = projects,
                                selectedProj = selectedProj,
                                currentScreen = currentScreen,
                                onSelectDashboard = {
                                    currentScreen = "dashboard"
                                    expandedProjectDrawer = false
                                },
                                onSelect = {
                                    viewModel.selectProject(it)
                                    currentScreen = "workbench"
                                    expandedProjectDrawer = false
                                },
                                onDelete = { viewModel.deleteProject(it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Running pipeline loader modal
            if (isRunning) {
                PipelineProgressOverlay(stepName = currentStep)
            }

            // Add dialogue modal
            if (showAddDialog) {
                AddNewProjectDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { title, repo, pr, diff, code, doc ->
                        viewModel.addNewProject(title, repo, pr, diff, code, doc)
                        currentScreen = "workbench"
                        showAddDialog = false
                    }
                )
            }
        }
    }
}


@Composable
fun ProjectHistorySidebar(
    projects: List<SyncProject>,
    selectedProj: SyncProject?,
    currentScreen: String,
    onSelectDashboard: () -> Unit,
    onSelect: (SyncProject) -> Unit,
    onDelete: (SyncProject) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(12.dp)
    ) {
        Text(
            text = "NAVIGATION",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Dashboard Button
        val isDashboardSelected = currentScreen == "dashboard"
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectDashboard() }
                .padding(bottom = 16.dp)
                .testTag("sidebar_dashboard_button"),
            colors = CardDefaults.cardColors(
                containerColor = if (isDashboardSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                1.dp,
                if (isDashboardSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Dashboard,
                    contentDescription = "Dashboard",
                    tint = if (isDashboardSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "PR Dashboard",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDashboardSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            text = "SYNC TASK QUEUE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(projects, key = { it.id }) { proj ->
                val isSelected = !isDashboardSelected && selectedProj?.id == proj.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(proj) }
                        .testTag("project_item_${proj.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Status Dot
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                val statusColor = when (proj.status) {
                                    "Completed", "Done" -> Color(0xFF10B981) // Green
                                    "Pending" -> Color(0xFF6B7280) // Grey
                                    "Analyzing", "SuggestionsDrafted" -> Color(0xFF3B82F6) // Blue
                                    else -> Color(0xFFF59E0B) // Amber
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PR #${proj.prNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = { onDelete(proj) },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("delete_project_${proj.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete task",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = proj.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Repo",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = proj.repoName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyWorkspaceWidget(onCreateRequested: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CodeOff,
                contentDescription = "Empty pipeline",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Sync Tasks Active",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Create a task or paste a code diff/PR patch. We'll use Gemini to analyze the diff against documentation files and automatically synthesize the synced modifications.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onCreateRequested) {
                Icon(Icons.Default.Add, "add icon")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start First Doc Sync")
            }
        }
    }
}

@Composable
fun ProjectWorkbench(
    project: SyncProject,
    viewModel: DocuSyncViewModel,
    apiKeyAvailable: Boolean,
    apiError: String?
) {
    val isRunning by viewModel.isRunningPipeline.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Code Diff Patch", "AI Sync Necessity", "Synced Output Docs")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Project Header Detail Card (Sleek Interface Active PR Card)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Top row with PR pill and live status label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge: PR #xxxx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PR #${project.prNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Processing status label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isCurrentRunning = project.status == "Analyzing" || isRunning
                        val infiniteTransition = rememberInfiniteTransition()
                        val alphaState = if (isCurrentRunning) {
                            infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )
                        } else {
                            remember { mutableStateOf(1.0f) }
                        }
                        val alpha = alphaState.value

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when (project.status) {
                                        "Completed", "Done" -> Color(0xFF10B981)
                                        "Pending" -> MaterialTheme.colorScheme.outline
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                    }
                                )
                        )

                        Text(
                            text = when (project.status) {
                                "Completed", "Done" -> "Completed"
                                "Pending" -> "Queued"
                                "Analyzing" -> "Analyzing"
                                else -> "Processing"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (project.status) {
                                "Completed", "Done" -> Color(0xFF10B981)
                                "Pending" -> MaterialTheme.colorScheme.outline
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title header text
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Branches info display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Source,
                            contentDescription = "Source Branch",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "main",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ForkLeft,
                            contentDescription = "Feature Branch",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "feature/docs-sync-patch",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Repository",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = project.repoName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (apiError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = apiError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Security info banner
                if (!apiKeyAvailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Alert",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Running in Preview Simulation Mode. Configure your real GEMINI_API_KEY in the Secrets panel inside Google AI Studio to run live Gemini 3.5 queries.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigator Tabs
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab views content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> DiffAndSandboxTab(project = project)
                1 -> EvaluationNecessityTab(project = project, viewModel = viewModel)
                2 -> SyncedOutputDocumentsTab(project = project, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun DiffAndSandboxTab(project: SyncProject) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // PR Diff Block
        SectionTitle(title = "Git Diff Unified Patch View")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                CodeDisplayViewer(text = project.diff, isDiff = true)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Original code context setup
        SectionTitle(title = "Source Code Structural Context")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF475569))
        ) {
            Box(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = project.codeContext,
                    color = Color(0xFFE2E8F0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Original doc context setup
        SectionTitle(title = "Active Repo Documentation Context")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF475569))
        ) {
            Box(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = project.docContext,
                    color = Color(0xFFF1F5F9),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun EvaluationNecessityTab(project: SyncProject, viewModel: DocuSyncViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Pipeline Progress Track (Sleek Interface)
        PipelineStatusCard(project = project)

        if (project.status == "Pending" || project.status == "Analyzing") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Run",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (project.status == "Pending") "Awaiting Sync Trigger" else "Pipeline Running...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (project.status == "Pending") "Click the 'Generate Doc Sync' floating action button below to trigger the multi-phase Gemini 1.5 Pro evaluation pipeline."
                                   else "Please wait while Gemini clockcycles your diff contexts and generates documentation files synchronously.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // Assessment Verdict Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (project.requiresUpdate) Color(0xFFEF4444).copy(alpha = 0.15f)
                            else Color(0xFF10B981).copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (project.requiresUpdate) "UPDATE REQUIRED" else "SYNCED / UP TO DATE",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (project.requiresUpdate) Color(0xFFEF4444) else Color(0xFF10B981)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    "Gemini AI Judgment",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Rationale Panel
            SectionTitle(title = "AI Code-Diff Rationale")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = project.necessityReasoning,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    lineHeight = 20.sp
                )
            }

            // Suggested actions
            val suggestions = viewModel.getParsedSuggestions(project)
            if (suggestions.isNotEmpty()) {
                SectionTitle(title = "Drafted Suggested Updates")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEachIndexed { index, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Target File: ${item.affected_file}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class StepStatusType {
    COMPLETED, ACTIVE, PENDING
}

@Composable
fun PipelineStatusCard(project: SyncProject) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Analysis",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Pipeline Status Trace",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val step1Status = StepStatusType.COMPLETED
            val step2Status = StepStatusType.COMPLETED

            val step3Status = when {
                project.status == "Pending" -> StepStatusType.PENDING
                project.status == "Analyzing" -> StepStatusType.ACTIVE
                else -> StepStatusType.COMPLETED
            }

            val step4Status = when {
                project.status == "Pending" || project.status == "Analyzing" -> StepStatusType.PENDING
                project.status == "NecessityAnalyzed" -> StepStatusType.ACTIVE
                else -> StepStatusType.COMPLETED
            }

            val step5Status = when {
                project.status == "SuggestionsDrafted" || project.status == "DocsUpdated" -> StepStatusType.ACTIVE
                project.status == "Done" || project.status == "Completed" -> StepStatusType.COMPLETED
                else -> StepStatusType.PENDING
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PipelineStepRow(
                    title = "PR Diff Fetched",
                    subtitle = "GithubGetDiffAction completed",
                    status = step1Status
                )

                PipelineStepRow(
                    title = "Context Indexed",
                    subtitle = "Code and Docs repositories loaded",
                    status = step2Status
                )

                PipelineStepRow(
                    title = "Necessity Analysis",
                    subtitle = if (step3Status == StepStatusType.COMPLETED) {
                        if (project.requiresUpdate) "Gemini: 'Documentation update required'"
                        else "Gemini: 'No documentation update needed'"
                    } else "Assessing documentation update necessity",
                    status = step3Status
                )

                PipelineStepRow(
                    title = "Generating Suggested Changes",
                    subtitle = "Drafting documentation modifications in detail",
                    status = step4Status
                )

                PipelineStepRow(
                    title = "File Updates & PR",
                    subtitle = "Preparing pull request for docs-repo and saving outputs",
                    status = step5Status
                )
            }
        }
    }
}

@Composable
fun PipelineStepRow(
    title: String,
    subtitle: String,
    status: StepStatusType
) {
    val opacity = if (status == StepStatusType.PENDING) 0.4f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(opacity),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon Circle
        when (status) {
            StepStatusType.COMPLETED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFB8F397)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color(0xFF002100),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            StepStatusType.ACTIVE -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing internal dot
                    val infiniteTransition = rememberInfiniteTransition()
                    val dotScale by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size((10 * dotScale).dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            StepStatusType.PENDING -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "Pending",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Description Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (status == StepStatusType.ACTIVE) FontWeight.Bold else FontWeight.Medium,
                color = if (status == StepStatusType.ACTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            // Line indicator under Active Step if needed
            if (status == StepStatusType.ACTIVE) {
                Spacer(modifier = Modifier.height(6.dp))
                // Clean visual linear tracker
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val progressOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun SyncedOutputDocumentsTab(project: SyncProject, viewModel: DocuSyncViewModel) {
    val fileUpdates = viewModel.getParsedFileUpdates(project)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    if (fileUpdates.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Await documents",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No Synchronies Compiled Yet",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (project.status == "Pending") "Run the Doc Sync evaluation to generate synced output."
                    else "No updates were deemed necessary for this PR code patch.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        var selectedFileIdx by remember { mutableStateOf(0) }
        val activeFile = fileUpdates.getOrNull(selectedFileIdx)

        var isEditMode by remember { mutableStateOf(false) }
        var editedContent by remember { mutableStateOf("") }

        // Whenever active file changes, initialize the text state
        LaunchedEffect(activeFile, isEditMode) {
            if (activeFile != null && !isEditMode) {
                editedContent = activeFile.content
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // File tab picker
            if (fileUpdates.size > 1) {
                ScrollableTabRow(selectedTabIndex = selectedFileIdx) {
                    fileUpdates.forEachIndexed { idx, update ->
                        Tab(
                            selected = selectedFileIdx == idx,
                            onClick = {
                                selectedFileIdx = idx
                                isEditMode = false
                            },
                            text = { Text(update.filename, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            activeFile?.let { file ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FilePresent,
                            contentDescription = "Markdown file",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = file.filename,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Edit Mode Toggle Button
                        IconButton(
                            onClick = {
                                if (isEditMode) {
                                    // Cancel editing or discard changes
                                    isEditMode = false
                                } else {
                                    editedContent = file.content
                                    isEditMode = true
                                }
                            },
                            modifier = Modifier.testTag("toggle_edit_button_${file.filename}")
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.EditOff else Icons.Default.Edit,
                                contentDescription = if (isEditMode) "Reader Mode" else "Edit Markdown",
                                tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Save Change button in edit mode
                        if (isEditMode) {
                            IconButton(
                                onClick = {
                                    viewModel.updateProjectDocContent(project, file.filename, editedContent)
                                    isEditMode = false
                                    Toast.makeText(context, "Saved changes locally!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("save_edit_button_${file.filename}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save changes",
                                    tint = Color(0xFF10B981)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(if (isEditMode) editedContent else file.content))
                                Toast.makeText(context, "Copied Markdown to Clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_button_${file.filename}")
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy Content")
                        }
                    }
                }

                // Dynamic Header Link Chips Row
                val headers = remember(file.content) {
                    file.content.split("\n")
                        .filter { it.startsWith("#") && it.contains(" ") && !it.contains("|") }
                        .map { it.trim() }
                }
                if (headers.isNotEmpty() && !isEditMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        headers.forEach { header ->
                            val level = header.takeWhile { it == '#' }.length
                            val cleanHeader = header.replace("#", "").trim()
                            AssistChip(
                                onClick = {
                                    Toast.makeText(context, "Outline: $cleanHeader", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(cleanHeader, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (level == 1) Icons.Default.Tag else Icons.Outlined.Label,
                                        contentDescription = "header link",
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isEditMode) {
                    // Document Sandbox Editor Text Box
                    OutlinedTextField(
                        value = editedContent,
                        onValueChange = { editedContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("markdown_editor_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        placeholder = { Text("Write Markdown here...") }
                    )
                } else {
                    // High Fidelity Markdown View Block
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MarkdownTextRenderer(content = file.content)
                            }
                        }
                    }
                }

                // Collapsible AI Refinement Panel
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AI Doc Revision Co-Pilot",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        var refinementPrompt by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = refinementPrompt,
                                onValueChange = { refinementPrompt = it },
                                placeholder = { Text("E.g., Translate to Spanish, add Java sample logic, or make comments detailed", fontSize = 11.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("refinement_prompt_input"),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )
                            Button(
                                onClick = {
                                    if (refinementPrompt.isNotBlank()) {
                                        viewModel.runDocuSyncRefinement(project, file.filename, refinementPrompt)
                                        refinementPrompt = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("refinement_prompt_submit_button")
                            ) {
                                Icon(Icons.Default.FlashOn, "Refine")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refine", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownTextRenderer(content: String) {
    // Simple high-fidelity pseudo markdown parser/renderer
    val lines = content.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var inCodeBlock = false
        val codeBlockLines = remember { mutableStateListOf<String>() }

        lines.forEach { line ->
            when {
                line.startsWith("```") -> {
                    if (inCodeBlock) {
                        // Close block and render it
                        inCodeBlock = false
                        CodeBlockSection(code = codeBlockLines.joinToString("\n"))
                        codeBlockLines.clear()
                    } else {
                        inCodeBlock = true
                    }
                }
                inCodeBlock -> {
                    codeBlockLines.add(line)
                }
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val raw = if (line.startsWith("- ")) line.removePrefix("- ") else line.removePrefix("* ")
                    Row(modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)) {
                        Text("•  ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = raw,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                line.startsWith("|") -> {
                    // Render simple table support visually beautifully
                    TableLineBlock(line = line)
                }
                line.trim().lowercase().startsWith("⚠️") || line.trim().lowercase().startsWith("* ⚠️") -> {
                    AlertNoticeBlock(message = line, isWarning = true)
                }
                line.trim().lowercase().startsWith("✅") || line.trim().lowercase().startsWith("* ✅") -> {
                    AlertNoticeBlock(message = line, isWarning = false)
                }
                else -> {
                    if (line.isNotBlank() && !line.startsWith("---") && !line.startsWith("|---")) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeBlockSection(code: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = code,
                color = Color(0xFF38BDF8),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun TableLineBlock(line: String) {
    val cells = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    if (cells.isNotEmpty() && !cells.first().contains("---")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cells.forEach { cell ->
                Text(
                    text = cell,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (cell.uppercase().contains("REQUIRED") || cell.contains("Parameter")) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun AlertNoticeBlock(message: String, isWarning: Boolean) {
    val bgColor = if (isWarning) Color(0xFFFFFBEB) else Color(0xFFF0FDF4)
    val borderColor = if (isWarning) Color(0xFFFDE68A) else Color(0xFFBBF7D0)
    val fontColor = if (isWarning) Color(0xFF92400E) else Color(0xFF166534)

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = fontColor,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun CodeDisplayViewer(text: String, isDiff: Boolean) {
    val scrollHorizontal = rememberScrollState()
    val scrollVertical = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollHorizontal)
            .verticalScroll(scrollVertical)
    ) {
        val annotatedText = if (isDiff) {
            buildAnnotatedString {
                val lines = text.split("\n")
                lines.forEach { line ->
                    val color = when {
                        line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF22C55E) // Green addition
                        line.startsWith("-") && !line.startsWith("---") -> Color(0xFFEF4444) // Red subtraction
                        line.startsWith("@@") -> Color(0xFF6366F1) // Indigo metadata
                        else -> Color(0xFF94A3B8) // Slate normal code
                    }
                    pushStyle(SpanStyle(color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp))
                    append(line + "\n")
                    pop()
                }
            }
        } else {
            buildAnnotatedString {
                append(text)
            }
        }

        Text(
            text = annotatedText,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun PipelineProgressOverlay(stepName: String) {
    // Elegant system blocking loading overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) {}, // absorb clicks
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(320.dp)
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "DocuSync Pipeline Active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Animated typing/dots text
                val infiniteTransition = rememberInfiniteTransition()
                val dotCount by infiniteTransition.animateValue(
                    initialValue = 0,
                    targetValue = 4,
                    typeConverter = Int.VectorConverter,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes { durationMillis = 1500 },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "dotCountAnimating"
                )
                val dots = ".".repeat(dotCount)

                Text(
                    text = stepName + dots,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    minLines = 2
                )
            }
        }
    }
}

@Composable
fun AddNewProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("github/my-app") }
    var pr by remember { mutableStateOf("12") }
    var diff by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf("") }

    var errors by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 12.dp)
                .testTag("add_project_dialog"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New PR Documentation Sync Task",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close form")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick Sandbox Scenarios Loader
                    Text(
                        text = "⚡ QUICK SANDBOX SCENARIOS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Scenario 1
                        SuggestionChip(
                            onClick = {
                                title = "Integrate PayPal Native SDK & Webhooks"
                                repo = "github/pay-gateway"
                                pr = "54"
                                diff = """
                                    diff --git a/PayPalController.kt b/PayPalController.kt
                                    new file mode 100644
                                    --- /dev/null
                                    +++ b/PayPalController.kt
                                    +package com.pay.gateway
                                    +
                                    +import com.paypal.android.sdk.payments.*
                                    +
                                    +class PayPalController {
                                    +    fun initCheckoutSession(amount: Double, currency: String, callback: (String) -> Unit) {
                                    +        val config = PayPalConfiguration().environment(PayPalConfiguration.ENVIRONMENT_SANDBOX)
                                    +        val payment = PayPalPayment(java.math.BigDecimal(amount), currency, "Cart Purchase")
                                    +        callback("PAY-SUCCESS-VAL-882")
                                    +    }
                                    +}
                                """.trimIndent()
                                code = """
                                    package com.pay.gateway
                                    
                                    class PaymentHub {
                                        fun routePayment() {
                                            println("Routing payment request through gateway server...")
                                        }
                                    }
                                """.trimIndent()
                                doc = """
                                    # Payment Integration Guild
                                    Welcome to pay-gateway checkout developer docs.
                                    
                                    ## Available Engines
                                    Currently we only support direct Stripe and traditional Bank Wire transfers.
                                """.trimIndent()
                            },
                            label = { Text("PayPal Checkout Integration", fontSize = 10.sp) }
                        )

                        // Scenario 2
                        SuggestionChip(
                            onClick = {
                                title = "Implement secure JWT Auth Token interceptor with Auto-Refresh"
                                repo = "github/vault-core"
                                pr = "142"
                                diff = """
                                    diff --git a/AuthInterceptor.kt b/AuthInterceptor.kt
                                    new file mode 100644
                                    --- /dev/null
                                    +++ b/AuthInterceptor.kt
                                    +package com.vault.core
                                    +
                                    +import okhttp3.Interceptor
                                    +import okhttp3.Response
                                    +
                                    +class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
                                    +    override fun intercept(chain: Interceptor.Chain): Response {
                                    +        val req = chain.request().newBuilder()
                                    +        if (tokenStore.isTokenExpired()) {
                                    +            val newJwtStr = tokenStore.refreshJwtSync()
                                    +            req.addHeader("Authorization", "Bearer ${"$"}{newJwtStr}")
                                    +        } else {
                                    +            req.addHeader("Authorization", "Bearer ${"$"}{tokenStore.getJwt()}")
                                    +        }
                                    +        return chain.proceed(req.build())
                                    +    }
                                    +}
                                """.trimIndent()
                                code = """
                                    package com.vault.core
                                    
                                    interface TokenStore {
                                        fun getJwt(): String
                                        fun isTokenExpired(): Boolean
                                        fun refreshJwtSync(): String
                                    }
                                """.trimIndent()
                                doc = """
                                    # Security and Authorization
                                    Security protocol configurations and interceptors.
                                    
                                    ## Token Lifecycle
                                    Clients must manually pass a static JWT token inside each authorization request. There is no auto-refresh or expiration tracking interceptor built yet.
                                """.trimIndent()
                            },
                            label = { Text("JWT Refresh Interceptor", fontSize = 10.sp) }
                        )

                        // Scenario 3
                        SuggestionChip(
                            onClick = {
                                title = "Refactor network engine to use Ktor Client with content negotiation"
                                repo = "github/rest-engine"
                                pr = "215"
                                diff = """
                                    diff --git "a/KtorClientFactory.kt" "b/KtorClientFactory.kt"
                                    new file mode 100644
                                    --- /dev/null
                                    +++ b/KtorClientFactory.kt
                                    +package com.rest.engine
                                    +
                                    +import io.ktor.client.*
                                    +import io.ktor.client.plugins.contentnegotiation.*
                                    +import io.ktor.serialization.kotlinx.json.*
                                    +
                                    +object KtorClientFactory {
                                    +    fun create(): HttpClient = HttpClient {
                                    +        install(ContentNegotiation) {
                                    +            json()
                                    +        }
                                    +    }
                                    +}
                                """.trimIndent()
                                code = """
                                    package com.rest.engine
                                    
                                    class RetrofitLegacyInstance {
                                        // Legacy Retrofit endpoints
                                    }
                                """.trimIndent()
                                doc = """
                                    # Rest Engine Connectivity
                                    We use Retrofit for standard API network connectivity.
                                """.trimIndent()
                            },
                            label = { Text("Ktor Client Engine", fontSize = 10.sp) }
                        )
                    }
                    
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title (e.g., Integrate Auth Service)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_title"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = repo,
                            onValueChange = { repo = it },
                            label = { Text("Github Repo Path") },
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("input_repo"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = pr,
                            onValueChange = { pr = it },
                            label = { Text("PR Number") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_pr"),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = diff,
                        onValueChange = { diff = it },
                        label = { Text("Git Patch Diff (GitHub format)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("input_diff"),
                        placeholder = {
                            Text(
                                "diff --git a/App.kt b/App.kt\n+ fun newMethod() {\n+    println()\n+ }",
                                fontSize = 11.sp
                            )
                        }
                    )

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Source Code Context (Relevant classes)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("input_code"),
                        placeholder = { Text("class MyAuthenticationService { ... }", fontSize = 11.sp) }
                    )

                    OutlinedTextField(
                        value = doc,
                        onValueChange = { doc = it },
                        label = { Text("Original Documentation Context (Markdown)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("input_doc"),
                        placeholder = { Text("# Security Protocol\nPIN access details...", fontSize = 11.sp) }
                    )

                    if (errors.isNotEmpty()) {
                        Text(text = errors, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val prNum = pr.toIntOrNull()
                            if (title.isBlank() || diff.isBlank() || prNum == null) {
                                errors = "Please fill in title, git diff patch, and correct numeric PR number."
                            } else {
                                onConfirm(title, repo, prNum, diff, code, doc)
                            }
                        },
                        modifier = Modifier.testTag("confirm_submit_button")
                    ) {
                        Text("Queue Pipeline Task")
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardView(
    projects: List<SyncProject>,
    onSelectProject: (SyncProject) -> Unit,
    onDeleteProject: (SyncProject) -> Unit,
    onCreateRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalPrs = projects.size
    val completedSyncs = projects.count { it.status == "Done" || it.status == "Completed" }
    val activeProcessing = projects.count { it.status == "Analyzing" || it.status == "SuggestionsDrafted" || it.status == "DocsUpdated" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Dashboard Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PR Sync Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Orchestrate multi-phase documentation synchronization across active code diffs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onCreateRequested,
                modifier = Modifier.testTag("dashboard_add_pr_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add PR")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add PR Diff")
            }
        }

        // Metrics Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricCard(
                title = "Total Pull Requests",
                value = "$totalPrs Active",
                icon = Icons.Default.Code,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Pipeline Completed",
                value = "$completedSyncs Synced",
                icon = Icons.Default.CheckCircle,
                borderColor = if (completedSyncs > 0) Color(0xFF10B981).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                textColor = if (completedSyncs > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "In Sync Flight",
                value = "$activeProcessing Processing",
                icon = Icons.Default.Autorenew,
                borderColor = if (activeProcessing > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                textColor = if (activeProcessing > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        // Recent PRs Table Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "All Project Sync Tasks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Responsive Table
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWide = maxWidth > 650.dp
                    
                    val prWeight = if (isWide) 0.38f else 0.45f
                    val repoWeight = if (isWide) 0.22f else 0.25f
                    val statusWeight = if (isWide) 0.18f else 0.30f
                    val updateWeight = 0.12f
                    val actionWeight = if (isWide) 0.10f else 0.15f

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PULL REQUEST & TITLE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(prWeight)
                            )
                            Text(
                                text = "REPOSITORY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(repoWeight)
                            )
                            Text(
                                text = "SYNC STATUS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(statusWeight)
                            )
                            if (isWide) {
                                Text(
                                    text = "UPDATE NEEDED",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(updateWeight)
                                )
                            }
                            Text(
                                text = "ACTION",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(actionWeight)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Table Body Row Items
                        if (projects.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No PR Sync tasks queued yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            projects.forEach { proj ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectProject(proj) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // PR Title Column
                                    Column(modifier = Modifier.weight(prWeight)) {
                                        Text(
                                            text = proj.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "PR #${proj.prNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Repository Column
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(repoWeight)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = "Repo icon",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = proj.repoName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Status Badge Column
                                    Box(modifier = Modifier.weight(statusWeight)) {
                                        val statusLabel = when (proj.status) {
                                            "Completed", "Done" -> "Completed"
                                            "Pending" -> "Queued"
                                            "Analyzing" -> "Analyzing"
                                            "SuggestionsDrafted" -> "Drafted"
                                            "DocsUpdated" -> "Docs Updated"
                                            else -> proj.status
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(
                                                        when (proj.status) {
                                                            "Completed", "Done" -> Color(0xFF10B981)
                                                            "Pending" -> MaterialTheme.colorScheme.outline
                                                            else -> MaterialTheme.colorScheme.primary
                                                        }
                                                    )
                                            )
                                            Text(
                                                text = statusLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = when (proj.status) {
                                                    "Completed", "Done" -> Color(0xFF10B981)
                                                    "Pending" -> MaterialTheme.colorScheme.outline
                                                    else -> MaterialTheme.colorScheme.primary
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Update Needed Column
                                    if (isWide) {
                                        Box(modifier = Modifier.weight(updateWeight)) {
                                            if (proj.status == "Pending" || proj.status == "Analyzing") {
                                                Text(
                                                    text = "TBD",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (proj.requiresUpdate) Color(0xFFEF4444).copy(alpha = 0.15f)
                                                            else Color(0xFF10B981).copy(alpha = 0.15f)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (proj.requiresUpdate) "Yes" else "No",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (proj.requiresUpdate) Color(0xFFEF4444) else Color(0xFF10B981)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Action Column
                                    Row(
                                        modifier = Modifier.weight(actionWeight),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { onSelectProject(proj) },
                                            modifier = Modifier.size(28.dp).testTag("select_project_btn_${proj.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = "View Details",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteProject(proj) },
                                            modifier = Modifier.size(28.dp).testTag("delete_project_btn_${proj.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete task",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    borderColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (textColor != MaterialTheme.colorScheme.onSurface) textColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

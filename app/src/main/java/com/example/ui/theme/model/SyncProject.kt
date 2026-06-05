package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_projects")
data class SyncProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val prNumber: Int,
    val repoName: String,
    val diff: String,
    val codeContext: String,
    val docContext: String,
    val necessityReasoning: String = "",
    val requiresUpdate: Boolean = false,
    val suggestions: String = "", // serialized array of suggestions
    val updatedDocs: String = "",  // serialized map of fileName -> content
    val status: String = "Pending", // Pending, Analyzing, NecessityAnalyzed, SuggestionsDrafted, DocsUpdated, Done
    val createdAt: Long = System.currentTimeMillis()
)

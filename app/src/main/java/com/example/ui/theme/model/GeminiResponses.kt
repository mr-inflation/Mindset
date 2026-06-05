package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NecessityResult(
    val requires_update: Boolean,
    val rationale: String
)

@JsonClass(generateAdapter = true)
data class SuggestionItem(
    val description: String,
    val affected_file: String
)

@JsonClass(generateAdapter = true)
data class SuggestionsResult(
    val suggestions: List<SuggestionItem>
)

@JsonClass(generateAdapter = true)
data class FileUpdate(
    val filename: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class FileUpdatesResult(
    val updates: List<FileUpdate>
)

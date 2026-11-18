package com.vikram.mindkeep.core.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Boundary for local inference engines (for example MediaPipe, LiteRT or llama.cpp).
 * Implementations must run inference on-device and must not silently fall back to a cloud API.
 */
interface OnDeviceAssistant {
    val availability: StateFlow<AssistantAvailability>
    suspend fun generate(prompt: String, relevantMemories: List<String> = emptyList()): AssistantResult
}

sealed interface AssistantAvailability {
    data object ModelNotInstalled : AssistantAvailability
    data object Ready : AssistantAvailability
    data class Unavailable(val reason: String) : AssistantAvailability
}

data class AssistantResult(
    val text: String,
    val referencedMemoryIds: List<String> = emptyList()
)

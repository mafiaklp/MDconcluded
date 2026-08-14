package com.brewtap.xbloom.domain

enum class BrewFeedbackType {
    TOO_SOUR,
    TOO_BITTER,
    TOO_WEAK,
    TOO_STRONG,
    TOO_DRY,
    MUTED,
}

data class BrewFeedback(
    val type: BrewFeedbackType,
    val note: String = "",
)

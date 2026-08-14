package com.brewtap.xbloom.domain

data class ExpectedCup(
    val acidity: Int,
    val sweetness: Int,
    val body: Int,
    val clarity: Int,
    val floral: Int,
    val fruit: Int,
    val bitternessRisk: Int,
    val summary: String,
) {
    init {
        listOf(acidity, sweetness, body, clarity, floral, fruit, bitternessRisk).forEach {
            require(it in 0..10) { "sensory scores must be 0..10" }
        }
        require(summary.isNotBlank())
    }
}

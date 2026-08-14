package com.brewtap.xbloom.photo

import com.brewtap.xbloom.domain.RoastLevel

data class AnalyzedCoffeeDraft(
    val name: String = "",
    val country: String = "",
    val region: String = "",
    val process: String = "",
    val roastLevel: RoastLevel = RoastLevel.UNKNOWN,
    val altitudeM: Int? = null,
    val tastingNotes: List<String> = emptyList(),
    val rawText: String = "",
)

object CoffeeBagAnalyzer {
    private val countries = listOf(
        "Ethiopia", "Kenya", "Colombia", "Brazil", "Panama", "Costa Rica", "Guatemala",
        "Rwanda", "Burundi", "Indonesia", "Thailand", "Peru", "El Salvador", "Honduras"
    )
    private val processes = listOf(
        "washed", "natural", "honey", "anaerobic", "thermal shock", "carbonic maceration", "wet hulled"
    )
    private val noteVocabulary = listOf(
        "jasmine", "bergamot", "floral", "berry", "blueberry", "strawberry", "raspberry",
        "peach", "apricot", "citrus", "lemon", "orange", "grapefruit", "mango", "pineapple",
        "tropical", "cherry", "blackcurrant", "honey", "caramel", "toffee", "chocolate", "cocoa",
        "tea", "vanilla", "wine", "grape", "plum", "apple", "pear"
    )

    fun analyze(textHints: String): AnalyzedCoffeeDraft {
        val clean = textHints.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        val lower = clean.lowercase()
        val country = countries.firstOrNull { lower.contains(it.lowercase()) }.orEmpty()
        val process = processes.firstOrNull { lower.contains(it) }.orEmpty()
        val roast = when {
            lower.contains("medium dark") || lower.contains("medium-dark") -> RoastLevel.MEDIUM_DARK
            lower.contains("medium light") || lower.contains("medium-light") -> RoastLevel.MEDIUM_LIGHT
            lower.contains("light roast") || lower.contains("light") -> RoastLevel.LIGHT
            lower.contains("dark roast") || lower.contains("dark") -> RoastLevel.DARK
            lower.contains("medium roast") || lower.contains("medium") -> RoastLevel.MEDIUM
            else -> RoastLevel.UNKNOWN
        }
        val altitude = Regex("(\\d{3,4})\\s*m(?:asl)?", RegexOption.IGNORE_CASE)
            .find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val notes = noteVocabulary.filter { lower.contains(it) }.distinct().take(8)
        val name = clean.split("/", "|", "•").firstOrNull()?.trim()?.take(60).orEmpty()

        return AnalyzedCoffeeDraft(
            name = name,
            country = country,
            process = process,
            roastLevel = roast,
            altitudeM = altitude,
            tastingNotes = notes,
            rawText = textHints,
        )
    }
}

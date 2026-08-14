package com.brewtap.xbloom

object XBloomCardImage {
    private const val RECIPE_OFFSET = 32
    private const val XID_SIZE = 7

    fun applyRecipe(originalCard: ByteArray, recipe: BrewRecipe): ByteArray {
        require(originalCard.size >= RECIPE_OFFSET + XID_SIZE) { "card image is too small" }
        val signature = originalCard.copyOfRange(0, RECIPE_OFFSET)
        val xid = originalCard.copyOfRange(RECIPE_OFFSET, RECIPE_OFFSET + XID_SIZE)
        val payload = XBloomRecipeEncoder.encodePayloadForCard(recipe, xid, signature)
        require(RECIPE_OFFSET + payload.size <= originalCard.size) { "recipe does not fit card image" }
        return originalCard.copyOf().also { out ->
            payload.copyInto(out, RECIPE_OFFSET)
            for (i in RECIPE_OFFSET + payload.size until out.size) out[i] = 0
        }
    }
}

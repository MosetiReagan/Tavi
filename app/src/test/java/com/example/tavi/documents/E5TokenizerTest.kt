package com.example.tavi.documents

import org.junit.Assert.*
import org.junit.Test

class E5TokenizerTest {
    private val tokenizer = E5Tokenizer.fromReader("""{"model":{"type":"Unigram","unk_id":3,"vocab":[["<s>",0],["<pad>",0],["</s>",0],["<unk>",0],["▁",-2],["a",-5],["b",-2],["▁a",-1],["ab",-3],["▁ab",-8]]}}""".reader())
    @Test fun segmentationUsesTheBestScoreRatherThanTheLongestPiece() {
        assertArrayEquals(longArrayOf(0, 7, 6, 2), tokenizer.encode("ab"))
    }
    @Test fun unknownSupplementaryCharactersAreFusedAndNeverSplitIntoSurrogates() {
        assertArrayEquals(longArrayOf(0, 4, 3, 2), tokenizer.encode("🦁🦒"))
    }
    @Test fun truncationPreservesTheEndTokenAndSpecialTokensRemainSpecial() {
        assertArrayEquals(longArrayOf(0, 7, 6, 2), tokenizer.encode("ab ab ab", 4))
        assertArrayEquals(longArrayOf(0, 0, 3, 1, 2), tokenizer.encode("<s><unk><pad>"))
    }
    @Test fun meanPoolingProducesAUnitVectorAndCosineRanksTheMatchingDirection() {
        val first = FloatArray(384).apply { this[0] = 3f; this[1] = 4f }
        val normalized = EmbeddingMath.meanNormalize(arrayOf(first, first))
        assertEquals(.6f, normalized[0], .0001f); assertEquals(.8f, normalized[1], .0001f)
        assertEquals(1.0, EmbeddingMath.cosine(normalized, normalized), .0001)
        assertEquals(0.0, EmbeddingMath.cosine(normalized, FloatArray(384).apply { this[2] = 1f }), .0001)
    }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteInferenceOutputIsRejected() {
        EmbeddingMath.meanNormalize(arrayOf(FloatArray(384).apply { this[0] = Float.NaN }))
    }
}

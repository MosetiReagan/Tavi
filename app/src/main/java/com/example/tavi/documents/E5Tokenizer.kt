package com.example.tavi.documents

import com.google.gson.stream.JsonReader
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Faithful SentencePiece precompiled normalization and maximum-score Unigram segmentation. */
class E5Tokenizer private constructor(private val normalizer: SentencePieceNormalizer?, private val trie: PieceTrie) {
    fun encode(text: String, maxLength: Int = 256): LongArray {
        require(maxLength in 2..512)
        val special = Regex("<s>|</s>|<pad>|<unk>|<mask>")
        val matches = special.findAll(text).toList()
        if (matches.isNotEmpty()) {
            val combined = ArrayList<Long>(); combined += 0L
            var start = 0
            val values = mapOf("<s>" to 0L, "<pad>" to 1L, "</s>" to 2L, "<unk>" to 3L, "<mask>" to 250001L)
            for (match in matches) {
                if (match.range.first > start) combined.addAll(encode(text.substring(start, match.range.first), maxLength).drop(1).dropLast(1))
                combined += values.getValue(match.value); start = match.range.last + 1
            }
            if (start < text.length) combined.addAll(encode(text.substring(start), maxLength).drop(1).dropLast(1))
            return (combined.take(maxLength - 1) + 2L).toLongArray()
        }
        val normalized = (normalizer?.normalize(text) ?: text).replace(Regex(" {2,}"), " ").replace(' ', '▁')
        val input = if (normalized.isNotEmpty() && !normalized.startsWith('▁')) "▁$normalized" else normalized
        val ids = ArrayList<Long>(maxLength)
        ids += 0L
        var start = 0
        while (start < input.length && ids.size < maxLength - 1) {
            val next = input.indexOf('▁', start + 1).let { if (it < 0) input.length else it }
            for (id in trie.segment(input.substring(start, next))) {
                if (ids.size == maxLength - 1) break
                ids += id.toLong()
            }
            start = next
        }
        ids += 2L
        return ids.toLongArray()
    }
    companion object {
        fun fromReader(reader: Reader): E5Tokenizer = JsonReader(reader).use { json ->
            var map: String? = null
            val trie = PieceTrie()
            var model = ""; var unknown = -1
            json.beginObject()
            while (json.hasNext()) when (json.nextName()) {
                "normalizer" -> {
                    json.beginObject()
                    while (json.hasNext()) {
                        if (json.nextName() != "normalizers") { json.skipValue(); continue }
                        json.beginArray()
                        while (json.hasNext()) {
                            json.beginObject()
                            while (json.hasNext()) when (json.nextName()) {
                                "precompiled_charsmap" -> map = json.nextString()
                                else -> json.skipValue()
                            }
                            json.endObject()
                        }
                        json.endArray()
                    }
                    json.endObject()
                }
                "model" -> {
                    json.beginObject()
                    while (json.hasNext()) when (json.nextName()) {
                        "type" -> model = json.nextString()
                        "unk_id" -> unknown = json.nextInt()
                        "vocab" -> {
                            json.beginArray()
                            var id = 0
                            while (json.hasNext()) {
                                json.beginArray(); val piece = json.nextString(); val score = json.nextDouble()
                                json.endArray(); trie.add(piece, score, id++)
                            }
                            json.endArray()
                        }
                        else -> json.skipValue()
                    }
                    json.endObject()
                }
                else -> json.skipValue()
            }
            json.endObject()
            require(model == "Unigram" && unknown == 3 && trie.tokens > 3) { "Unsupported embedding tokenizer." }
            E5Tokenizer(map?.let { SentencePieceNormalizer(Base64.getDecoder().decode(it)) }, trie.apply { compact() })
        }
    }
}

internal class SentencePieceNormalizer(data: ByteArray) {
    private val units: IntArray
    private val strings: ByteArray
    init {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val size = buffer.int
        require(size > 0 && size % 4 == 0 && size <= data.size - 4)
        units = IntArray(size / 4) { buffer.int }
        strings = data.copyOfRange(size + 4, data.size)
    }
    private fun offset(unit: Int) = (unit ushr 10) shl ((unit and (1 shl 9)) ushr 6)
    // Hugging Face's precompiled normalizer takes the first trie prefix and operates
    // on graphemes shorter than six UTF-8 bytes, then individual code points.
    private fun transform(bytes: ByteArray): String? {
        var node = offset(units[0]); var replacement = -1
        for (byte in bytes) {
            val label = byte.toInt() and 255
            if (label == 0) break
            val index = node xor label
            if (index !in units.indices) break
            val unit = units[index]
            if ((unit and 0x800000ff.toInt()) != label) break
            node = index xor offset(unit)
            if ((unit and 256) != 0 && node in units.indices) { replacement = units[node] and 0x7fffffff; break }
        }
        if (replacement < 0) return null
        require(replacement in strings.indices)
        var end = replacement
        while (end < strings.size && strings[end] != 0.toByte()) end++
        return String(strings, replacement, end - replacement, Charsets.UTF_8)
    }
    fun normalize(text: String): String {
        val output = StringBuilder(text.length)
        for (match in Regex("\\X").findAll(text)) {
            val grapheme = match.value
            val bytes = grapheme.toByteArray(Charsets.UTF_8)
            val mapped = if (bytes.size < 6) transform(bytes) else null
            if (mapped != null) output.append(mapped)
            else {
                var position = 0
                while (position < grapheme.length) {
                    val count = Character.charCount(Character.codePointAt(grapheme, position))
                    val part = grapheme.substring(position, position + count)
                    output.append(transform(part.toByteArray(Charsets.UTF_8)) ?: part)
                    position += count
                }
            }
        }
        return output.toString()
    }
}

/** Array-backed trie avoids a per-character HashMap/object for a quarter-million vocabulary. */
private class PieceTrie {
    private var chars = CharArray(524288)
    private var child = IntArray(chars.size)
    private var sibling = IntArray(chars.size)
    private var ids = IntArray(chars.size) { -1 }
    private var scores = DoubleArray(chars.size)
    private val rootEdges = IntArray(65536)
    private var nodes = 1
    var tokens = 0; private set
    private var minimum = 0.0
    fun add(piece: String, score: Double, id: Int) {
        tokens++
        minimum = minOf(minimum, score)
        if (piece.isEmpty()) return
        var node = 0
        for (c in piece) {
            var next = if (node == 0) rootEdges[c.code] else child[node]
            if (node != 0) while (next != 0 && chars[next] != c) next = sibling[next]
            if (next == 0) {
                if (nodes == chars.size) grow(chars.size * 2)
                next = nodes++; chars[next] = c; sibling[next] = child[node]; child[node] = next
                if (node == 0) rootEdges[c.code] = next
            }
            node = next
        }
        ids[node] = id; scores[node] = score
    }
    private fun grow(size: Int) {
        val previous = ids.size
        chars = chars.copyOf(size); child = child.copyOf(size); sibling = sibling.copyOf(size)
        ids = ids.copyOf(size); if (size > previous) java.util.Arrays.fill(ids, previous, size, -1); scores = scores.copyOf(size)
    }
    fun compact() { grow(nodes) }
    fun segment(text: String): List<Int> {
        val best = DoubleArray(text.length + 1) { Double.NEGATIVE_INFINITY }; best[0] = 0.0
        val from = IntArray(best.size); val token = IntArray(best.size) { -1 }
        var start = 0
        while (start < text.length) {
            val codepointEnd = start + Character.charCount(Character.codePointAt(text, start))
            if (best[start] != Double.NEGATIVE_INFINITY) {
                var node = 0; var cursor = start; var hasSingle = false
                while (cursor < text.length) {
                    val c = text[cursor]
                    var next = if (node == 0) rootEdges[c.code] else child[node]
                    if (node != 0) while (next != 0 && chars[next] != c) next = sibling[next]
                    if (next == 0) break
                    node = next; cursor++
                    if (ids[node] >= 0) {
                        if (cursor == codepointEnd) hasSingle = true
                        val candidate = best[start] + scores[node]
                        if (candidate > best[cursor]) { best[cursor] = candidate; from[cursor] = start; token[cursor] = ids[node] }
                    }
                }
                if (!hasSingle) {
                    val candidate = best[start] + minimum - 10.0
                    if (candidate > best[codepointEnd]) { best[codepointEnd] = candidate; from[codepointEnd] = start; token[codepointEnd] = 3 }
                }
            }
            start = codepointEnd
        }
        val reversed = ArrayList<Int>(); var end = text.length
        while (end > 0) {
            check(token[end] >= 0 && from[end] < end) { "Unigram segmentation failed." }
            val id = token[end]
            if (id != 3 || reversed.lastOrNull() != 3) reversed += id
            end = from[end]
        }
        reversed.reverse(); return reversed
    }
}

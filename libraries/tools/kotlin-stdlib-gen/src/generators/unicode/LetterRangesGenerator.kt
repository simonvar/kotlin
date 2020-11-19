/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode

import generators.requireExistingDir
import templates.KotlinTarget
import java.io.File
import java.io.FileWriter

internal class LetterRangesGenerator(
    private val outputFile: File,
    target: KotlinTarget
) : UnicodeDataGenerator {
    private val ranges = mutableListOf<RangePattern>()
    private val writingStrategy = RangesWritingStrategy.of(target, "LetterRangesWrapper")

    private val letterCategoryCodes = listOf(
        CharCategory.UPPERCASE_LETTER.code,
        CharCategory.LOWERCASE_LETTER.code,
        CharCategory.TITLECASE_LETTER.code,
        CharCategory.MODIFIER_LETTER.code,
        CharCategory.OTHER_LETTER.code
    )

    init {
        outputFile.parentFile.requireExistingDir()
    }

    override fun appendChar(char: String, name: String, categoryCode: String) {
        if (categoryCode !in letterCategoryCodes) {
            return
        }

        val charCode = char.toInt(radix = 16)
//        println("$char $categoryCode $name")

        if (ranges.isEmpty() || name.endsWith(", First>")) {
            ranges.add(ConsequentPattern(charCode))
            return
        }

        val lastRange = ranges.last()

        if (name.endsWith(", Last>")) {
            check(lastRange is ConsequentPattern)
            lastRange.setEnd(charCode)
            return
        }

        val newLastRange = lastRange.append(charCode)
        if (newLastRange != null) {
            ranges[ranges.lastIndex] = newLastRange
        } else {
//            println(lastRange)
            ranges.add(ConsequentPattern(charCode))
        }
    }

    override fun close() {
        FileWriter(outputFile).use { writer ->
            writer.writeHeader(outputFile, "kotlin.text")
            writer.appendLine()
            writer.writeRanges()
            writer.appendLine()
            writer.appendLine(isLetterImpl())
        }
    }

    private fun FileWriter.writeRanges() {
        writingStrategy.beforeWritingRanges(this)
        writeIntArray("rangeStart", ranges.map { it.rangeStart() }, writingStrategy)
        appendLine()
        writeIntArray("rangeEnd", ranges.map { it.rangeEnd() }, writingStrategy)
        writingStrategy.afterWritingRanges(this)
    }

    private fun isLetterImpl(): String {
        val rangeStart = writingStrategy.rangeReference("rangeStart")
        val rangeEnd = writingStrategy.rangeReference("rangeEnd")
        return """
        /**
         * Returns `true` if this character is a letter.
         */
        internal fun Char.isLetterImpl(): Boolean {
            val ch = this.toInt()
            val index = binarySearchRange($rangeStart, ch)

            val rangeStart = $rangeStart[index]
            val rangeEnd = $rangeEnd[index]

            val isGapPattern = rangeEnd > 0xffff
            if (isGapPattern) {
                if (ch > rangeEnd and 0xffff) {
                    return false
                }
                val charsBeforeGap = rangeEnd shr 24
                val gapLength = (rangeEnd shr 16) and 0xff
                val chDistance = ch - rangeStart
                return chDistance < charsBeforeGap || chDistance >= charsBeforeGap + gapLength
            }

            val isBitPattern = rangeEnd < 0
            if (isBitPattern) {
                if (ch > rangeStart + 31) {
                    return false
                }
                val shift = ch - rangeStart - 1
                return (ch == rangeStart) || rangeEnd and (1 shl shift) > 0
            }

            return ch <= rangeEnd
        }
        """.trimIndent()
    }
}

private interface RangePattern {
    fun append(charCode: Int): RangePattern?
    fun rangeStart(): Int
    fun rangeEnd(): Int
}

/**
 * A range of consequent letters
 */
private class ConsequentPattern(val start: Int) : RangePattern {
    var end = start
        private set

    fun setEnd(end: Int) {
        assert(this.end < end)
        this.end = end
    }

    override fun append(charCode: Int): RangePattern? {
        if (end == charCode - 1) {
            end = charCode
            return this
        }
        return GapPattern.from(this, charCode)
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun toString(): String {
        return "ConsequentPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                "}"
    }
}

/**
 * A range of consequent chars that starts with a letter and ends with a letter, and contains a single consequent not-letter chars range.
 *
 * Divide the returned [rangeEnd] int value in 3 parts as follows: 0x7f_ff_ffff
 * The right-hand side part (ffff) is the end of the char range - [end]
 * The middle part (ff) is the length of the gap in the char range - [gapLength]
 * The left-hand side part (7f) is the number of letters before the gap in the char range - [charsBeforeGap]
 */
private class GapPattern(val start: Int, val charsBeforeGap: Int, val gapLength: Int, var end: Int) : RangePattern {

    init {
        assert(isValid(charsBeforeGap, gapLength))
    }

    val charsAfterGap: Int
        get() = end - (start + charsBeforeGap + gapLength) + 1

    override fun append(charCode: Int): RangePattern? {
        // lll_gap_lll_X_l
        if (end == charCode - 1) {
            // _X_ is empty -> append the letter
            end = charCode
            return this
        }
        // _X_ is a new gap -> try to convert to a bit pattern
        return BitPattern.from(this, charCode)
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return (charsBeforeGap shl 24) or (gapLength shl 16) or end
    }

    override fun toString(): String {
        return "GapPattern{" +
                "start=" + start.hex() +
                ", charsBeforeGap=" + charsBeforeGap +
                ", gapLength=" + gapLength +
                ", charsAfterGap=" + charsAfterGap +
                ", end=" + end.hex() +
                "}"
    }

    companion object {
        fun from(range: ConsequentPattern, charCode: Int): GapPattern? {
            val charsBeforeGap = range.end - range.start + 1
            val gapLength = charCode - range.end - 1
            return if (isValid(charsBeforeGap, gapLength))
                GapPattern(range.start, charsBeforeGap, gapLength, end = charCode)
            else
                null
        }

        private fun isValid(charsBeforeGap: Int, gapLength: Int): Boolean {
            return charsBeforeGap <= 0x7f && gapLength <= 0xff
        }
    }
}

/**
 * A range of 32 consequent chars.
 *
 * The first char in the range is the [start].
 *
 * The returned [rangeEnd] int value describes 31 chars that follow [start] the following way:
 * The leftmost bit (sign bit) is reserved and is 1
 * The char that immediately follows [start] is described in the rightmost bit
 * The next char is described in the second (from the right) bit, and so on
 * If a char in the range is a letter, the corresponding bit is set to 1, otherwise the bit is set to 0
 */
private class BitPattern(val start: Int, private var pattern: Int) : RangePattern {

    init {
        assert(pattern < 0)
    }

    override fun append(charCode: Int): RangePattern? {
        if (charCode - start <= 31) {
            val shift = charCode - start - 1
            pattern = pattern or (1 shl shift)
            return this
        }
        return null
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return pattern
    }

    override fun toString(): String {
        return "BitPattern{" +
                "start=" + start.hex() +
                ", pattern=1" + (pattern shl 1 ushr 1).toString(2) +
                ", end=" + (start + 31).hex() +
                "}"
    }

    companion object {
        fun from(range: GapPattern, charCode: Int): BitPattern? {
            if (!isValid(range.start, charCode)) {
                return null
            }
            val charsBeforeGap = range.charsBeforeGap - 1 // the first char is [start] -> skip it here
            val bitsBeforeFirstGap = (1 shl charsBeforeGap) - 1
            val bitsAfterFirstGap = ((1 shl range.charsAfterGap) - 1) shl (charsBeforeGap + range.gapLength)
            val shift = charCode - range.start - 1
            val charCodeBit = 1 shl shift
            val signBit = 1 shl 31
            val pattern = signBit /*spare bits*/ or charCodeBit /*second gap*/ or bitsAfterFirstGap /*first gap*/ or bitsBeforeFirstGap
            return BitPattern(range.start, pattern)
        }

        private fun isValid(start: Int, charCode: Int): Boolean {
            return charCode - start <= 31
        }
    }
}
/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges

import generators.unicode.hex

internal interface RangePattern {
    fun append(charCode: Int): RangePattern?
    fun rangeStart(): Int
    fun rangeEnd(): Int
}

/**
 * A range of consequent letters
 */
internal class ConsequentPattern(val start: Int) : RangePattern {
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
 * Divide the returned [rangeEnd] int value in 3 parts as follows: 0x3f_ff_ffff
 * The right-hand side part (ffff) is the end of the char range - [end]
 * The middle part (ff) is the length of the gap in the char range - [gapLength]
 * The left-hand side part (3f) is the number of letters before the gap in the char range - [charsBeforeGap]
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
            return charsBeforeGap <= 0x3f && gapLength <= 0xff
        }
    }
}

/**
 * A range of 31 consequent chars.
 *
 * The first char in the range is the [start].
 *
 * The returned [rangeEnd] int value describes 30 chars that follow [start] the following way:
 * The leftmost bit (right to the sign bit) is reserved and is 1
 * The char that immediately follows [start] is described in the rightmost bit
 * The next char is described in the second (from the right) bit, and so on
 * If a char in the range is a letter, the corresponding bit is set to 1, otherwise the bit is set to 0
 */
private class BitPattern(val start: Int, private var pattern: Int) : RangePattern {

    init {
        assert(pattern < 0)
    }

    override fun append(charCode: Int): RangePattern? {
        if (charCode - start <= 30) {
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
                ", end=" + (start + 30).hex() +
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
            val patternBit = 1 shl 30
            val pattern = patternBit /*spare bits*/ or charCodeBit /*second gap*/ or bitsAfterFirstGap /*first gap*/ or bitsBeforeFirstGap
            return BitPattern(range.start, pattern)
        }

        private fun isValid(start: Int, charCode: Int): Boolean {
            return charCode - start <= 30
        }
    }
}

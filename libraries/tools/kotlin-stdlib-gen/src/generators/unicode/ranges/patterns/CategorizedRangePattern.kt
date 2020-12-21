/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges.patterns

import generators.unicode.ranges.writers.hex

/**
 * A range of consequent chars that fit within a particular pattern.
 */
internal interface CategorizedRangePattern {
    /**
     * Appends the [charCode] to this range pattern.
     * Returns this instance if the [charCode] with the specified [categoryCode] could be accommodated within this pattern,
     * or a new pattern containing all chars in this pattern and the [charCode],
     * or `null` if no pattern could accommodate all chars in this pattern along with [charCode].
     */
    fun append(charCode: Int, categoryCode: String): Boolean

    /**
     * Prepends the [charCode] to this range pattern.
     * Returns this instance if the [charCode] with the specified [categoryCode] could be accommodated within this pattern,
     * or `null` if [charCode] doesn't fit this range.
     *
     * Note that unlike the [append] function, this function doesn't create a new pattern.
     */
    fun prepend(charCode: Int, categoryCode: String): Boolean

    /**
     * Char code of the first char in this range.
     */
    fun rangeStart(): Int

    /**
     * Char code of the last char in this range.
     */
    fun rangeEnd(): Int

    /**
     * An integer value that contains information about the category of each char in this range.
     */
    fun category(): Int

    /**
     * Returns category code of the char with the specified [charCode].
     * Throws an exception if the [charCode] is not in `rangeStart()..rangeEnd()`.
     */
    fun categoryCodeOf(charCode: Int): String
}

internal fun CategorizedRangePattern.rangeLength(): Int = rangeEnd() - rangeStart() + 1


/**
 * A range of consequent chars having three periodic categories, e.g., [Lu, Ll, Lo, Lu, ...].
 */
internal class CategorizedPeriodicPattern private constructor(
    private var start: Int,
    private var end: Int,
    private val bag: Bag,
    private val makeCategory: (Array<String>) -> Int
) : CategorizedRangePattern {
    val period: Int get() = bag.period

    override fun append(charCode: Int, categoryCode: String): Boolean {
        require(charCode > end)
        if (!bag.fill(end + 1, charCode - 1, { bag.unassignedCategoryCode }, charCode, categoryCode)) {
            return false
        }
        end = charCode
        return true
    }

    override fun prepend(charCode: Int, categoryCode: String): Boolean {
        require(charCode < start)
        if (!bag.fill(charCode + 1, start - 1, { bag.unassignedCategoryCode }, charCode, categoryCode)) {
            return false
        }
        start = charCode
        return true
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        return makeCategory(orderedCategoryCodes())
    }

    private fun orderedCategoryCodes(): Array<String> {
        val size = minOf(period, rangeLength())
        val ordered = Array(size) { bag.unassignedCategoryCode }
        for (i in 0 until size) {
            ordered[i] = categoryCodeOf(start + i)
        }
        return ordered;
    }

    override fun categoryCodeOf(charCode: Int): String {
        if (charCode !in start..end) {
            throw IllegalArgumentException("Char code ${charCode.hex()} is not in $this")
        }
        val categoryCode = bag.categoryCodeOf(charCode)
        check(categoryCode.isNotEmpty())
        return categoryCode
    }

    override fun toString(): String {
        return "CategorizedPeriodicPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", length=" + rangeLength() +
                ", orderedCategoryCodes=" + orderedCategoryCodes().contentToString() +
                ", bag=" + bag +
                "}"
    }

    companion object {
        fun from(
            range: CategorizedRangePattern,
            charCode: Int,
            categoryCode: String,
            period: Int,
            isPeriodic: Boolean,
            unassignedCategoryCode: String,
            makeCategory: (Array<String>) -> Int
        ): CategorizedPeriodicPattern? {
            val bag = Bag(period, isPeriodic, unassignedCategoryCode)
            if (bag.fill(range, charCode, categoryCode)) {
                return CategorizedPeriodicPattern(range.rangeStart(), charCode, bag, makeCategory)
            }
            return null
        }

        fun from(
            charCode: Int,
            categoryCode: String,
            period: Int,
            isPeriodic: Boolean,
            unassignedCategoryCode: String,
            makeCategory: (Array<String>) -> Int
        ): CategorizedPeriodicPattern {
            val bag = Bag(period, isPeriodic, unassignedCategoryCode)
            check(bag.fill(charCode, categoryCode))
            return CategorizedPeriodicPattern(charCode, charCode, bag, makeCategory)
        }
    }
}

private class Bag(
    val period: Int,
    private val isPeriodic: Boolean,
    val unassignedCategoryCode: String
) {
    val categoryCodes = Array(period) { "" }

    fun categoryCodeOf(charCode: Int): String {
        return categoryCodes[charCode % period]
    }

    fun fill(range: CategorizedRangePattern, charCode: Int, categoryCode: String): Boolean {
        return fill(range.rangeStart(), range.rangeEnd(), range::categoryCodeOf, charCode, categoryCode)
    }

    fun fill(rangeStart: Int, rangeEnd: Int, categoryCodeOf: (Int) -> String, charCode: Int, categoryCode: String): Boolean {
        val attempt = categoryCodes.copyOf()

        for (ch in rangeStart..rangeEnd) {
            if (!attempt.fill(ch, categoryCodeOf(ch))) return false
        }
        for (ch in rangeEnd + 1 until charCode) { // append
            if (!attempt.fill(ch, unassignedCategoryCode)) return false
        }
        for (ch in charCode + 1 until rangeStart) { // prepend
            if (!attempt.fill(ch, unassignedCategoryCode)) return false
        }
        if (!attempt.fill(charCode, categoryCode)) return false

        attempt.copyInto(categoryCodes)
        return true
    }

    fun fill(charCode: Int, categoryCode: String): Boolean {
        return categoryCodes.fill(charCode, categoryCode)
    }

    private fun Array<String>.fill(charCode: Int, categoryCode: String): Boolean {
        val index = charCode % period   // place them by distance from start
        val expected = this[index]
        if (expected.isEmpty() || (isPeriodic && expected == categoryCode)) {
            this[index] = categoryCode
            return true
        }
        return false
    }

    override fun toString(): String {
        return "Bag{" +
                "period=" + period +
                ", isPeriodic=" + isPeriodic +
                ", unassignedCategoryCode=" + unassignedCategoryCode +
                ", categoryCodes=" + categoryCodes.contentToString() +
                "}"
    }
}


/**
 * A range of consequent chars that starts with a letter and ends with a letter, and contains a single consequent not-letter chars range.
 *
 * Divide the returned [rangeEnd] int value in 3 parts as follows: 0x7f_ff_ffff
 * The right-hand side part (ffff) is the end of the char range - [end]
 * The middle part (ff) is the length of the gap in the char range - [gapLength]
 * The left-hand side part (7f) is the number of letters before the gap in the char range - [beforeGapLength]
 */
internal class GapRangePattern private constructor(
    private val start: Int,
    private var end: Int,
    private val categoryCode: String,
    private val unassignedCategoryCode: String,
    private val makeCategory: (List<Int>, List<Int>) -> Int
) : CategorizedRangePattern {
    private val list = mutableListOf<String>()

    private val beforeGapLength = mutableListOf<Int>()
    private val gapLength = mutableListOf<Int>()
    private var charAfterLastGap = end

    init {
        repeat(end - start + 1) {
            list.add(categoryCode)
        }
        require(categoryCode == "OL")
    }

    override fun append(charCode: Int, categoryCode: String): Boolean {
        require(charCode > end)

        if (categoryCode == unassignedCategoryCode) {
            return true
        }

        if (categoryCode != this.categoryCode) {
            return false
        }

        // lll_gap_lll_X_l
        if (end == charCode - 1) {
            // _X_ is empty -> append the letter
            list.add(categoryCode)
            end = charCode
            return true
        }

        val charsBeforeNewGap = end - charAfterLastGap + 1
        val newGapLength = charCode - end - 1
        val bits = (beforeGapLength.size + 1) * (CHARS_BITS + GAP_BITS)

        if (isValid(charsBeforeNewGap, newGapLength) && bits <= TOTAL_BITS) {
            repeat(newGapLength) {
                list.add(unassignedCategoryCode)
            }
            list.add(categoryCode)

            beforeGapLength.add(charsBeforeNewGap)
            gapLength.add(newGapLength)
            charAfterLastGap = charCode
            end = charCode
            return true
        }

        return false
    }

    override fun prepend(charCode: Int, categoryCode: String): Boolean {
        assert(charCode < start)
        return false
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        return makeCategory(beforeGapLength, gapLength)
    }

    override fun categoryCodeOf(charCode: Int): String {
        require(charCode in start..end)
        return list[charCode - start]
    }

    override fun toString(): String {
        return "GapPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", length=" + rangeLength() +
                ", beforeGapLength=" + beforeGapLength +
                ", gapLength=" + gapLength +
                ", charAfterLastGap=" + charAfterLastGap.hex() +
                ", categoryCode=" + categoryCode +
                "}"
    }

    companion object {
        internal const val CHARS_BITS = 7
        internal const val GAP_BITS = 7
        private const val TOTAL_BITS = 31

        fun from(
            range: CategorizedRangePattern,
            charCode: Int,
            categoryCode: String,
            unassignedCategoryCode: String,
            makeCategory: (List<Int>, List<Int>) -> Int
        ): CategorizedRangePattern? {
            val start = range.rangeStart()
            val startCategoryCode = range.categoryCodeOf(start)

            check(startCategoryCode != unassignedCategoryCode)

            if (startCategoryCode != categoryCode || categoryCode != "OL") return null

            val gapRange = GapRangePattern(start, start, startCategoryCode, unassignedCategoryCode, makeCategory)

            for (ch in start + 1..range.rangeEnd()) {
                if (!gapRange.append(ch, range.categoryCodeOf(ch))) {
                    return null
                }
            }
            if (!gapRange.append(charCode, categoryCode)) {
                return null
            }

            return gapRange
        }

        private fun isValid(charsBeforeGap: Int, gapLength: Int): Boolean {
            return charsBeforeGap < (1 shl CHARS_BITS) && gapLength < (1 shl GAP_BITS)
        }
    }
}
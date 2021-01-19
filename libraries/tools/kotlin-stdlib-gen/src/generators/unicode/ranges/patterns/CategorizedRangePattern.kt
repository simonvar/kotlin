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
     * Returns true if the [charCode] with the specified [categoryCode] could be accommodated within this pattern.
     * Returns false otherwise.
     */
    fun append(charCode: Int, categoryCode: String): Boolean

    /**
     * Prepends the [charCode] to this range pattern.
     * Returns true if the [charCode] with the specified [categoryCode] could be accommodated within this pattern.
     * Returns false otherwise.
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
 * A range of consequent chars.
 *
 * The chars in the range may have periodic categories, e.g., [Lu, Ll, Lu, Ll, ...].
 *
 * @param charCode the start of this range
 * @param categoryCode the category code of the char with the specified [charCode]
 * @param sequenceLength the maximum length this range can have.
 *      If [isPeriodic] is true than this range can be longer with:
 *      for every `charCode >= start + sequenceLength` categoryCodeOf(charCode) is equal to categoryCodeOf(charCode - sequenceLength)
 * @param isPeriodic true if this range is a periodic range with period [sequenceLength]
 * @param unassignedCategoryCode the categoryCode of the unassigned chars.
 *      Chars that are not appended or prepended are considered to be unassigned
 * @param makeCategory the function used to transform this range to an Int representation that is returned from the [category] function.
 *      [makeCategory] is called with an array having its size equal to `minOf(sequenceLength, rangeLength())`.
 */
internal class CategorizedPeriodicPattern private constructor(
    charCode: Int,
    categoryCode: String,
    val sequenceLength: Int,
    isPeriodic: Boolean,
    unassignedCategoryCode: String,
    private val makeCategory: (Array<String>) -> Int
) : CategorizedRangePattern {
    private var start: Int = charCode
    private var end: Int = charCode
    private val bag: Bag = Bag(sequenceLength, isPeriodic, unassignedCategoryCode)

    init {
        bag.fill(charCode, categoryCode)
    }

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
        val size = minOf(sequenceLength, rangeLength())
        return Array(size) { categoryCodeOf(start + it) }
    }

    override fun categoryCodeOf(charCode: Int): String {
        if (charCode !in start..end) {
            throw IllegalArgumentException("Char code ${charCode.hex()} is not in $this")
        }
        val categoryCode = bag.categoryCodeOf(charCode)
        check(categoryCode != null)
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
            sequenceLength: Int,
            isPeriodic: Boolean,
            unassignedCategoryCode: String,
            makeCategory: (Array<String>) -> Int
        ): CategorizedPeriodicPattern? {
            require(charCode > range.rangeEnd())

            val start = range.rangeStart()
            val newRange = from(start, range.categoryCodeOf(start), sequenceLength, isPeriodic, unassignedCategoryCode, makeCategory)
            if (newRange.append(start + 1, range.rangeEnd(), range::categoryCodeOf, charCode, categoryCode)) {
                return newRange
            }
            return null
        }

        fun from(
            charCode: Int,
            categoryCode: String,
            sequenceLength: Int,
            isPeriodic: Boolean,
            unassignedCategoryCode: String,
            makeCategory: (Array<String>) -> Int
        ): CategorizedPeriodicPattern {
            return CategorizedPeriodicPattern(charCode, categoryCode, sequenceLength, isPeriodic, unassignedCategoryCode, makeCategory)
        }
    }
}

/**
 * A set of chars with their corresponding categories.
 *
 * Category code of a char with code equal to `charCode` is placed at index `charCode % sequenceLength` of the [categoryCodes].
 */
private class Bag(
    private val sequenceLength: Int,
    private val isPeriodic: Boolean,
    val unassignedCategoryCode: String
) {
    private val categoryCodes = arrayOfNulls<String>(sequenceLength)

    fun categoryCodeOf(charCode: Int): String? {
        return categoryCodes[charCode % sequenceLength]
    }

    /**
     * Returns true if a range with the specified [rangeStart], [rangeEnd] and [categoryCodeOf] was successfully added
     * together with a char with the specified [charCode] and [categoryCode].
     *
     * The [charCode] must go immediately after the [rangeEnd] or before the [rangeStart].
     */
    fun fill(rangeStart: Int, rangeEnd: Int, categoryCodeOf: (Int) -> String, charCode: Int, categoryCode: String): Boolean {
        require(charCode == rangeStart - 1 || charCode == rangeEnd + 1)

        val attempt = categoryCodes.copyOf()

        for (ch in rangeStart..rangeEnd) {
            if (!attempt.fill(ch, categoryCodeOf(ch))) return false
        }
        if (!attempt.fill(charCode, categoryCode)) return false

        attempt.copyInto(categoryCodes)
        return true
    }

    /**
     * Returns true if the [charCode] with the [categoryCode] was successfully placed in [categoryCodes].
     */
    fun fill(charCode: Int, categoryCode: String): Boolean {
        return categoryCodes.fill(charCode, categoryCode)
    }

    /**
     * Returns true if the [charCode] with the [categoryCode] was successfully placed in this array.
     *
     * The [charCode] is placed at index `charCode % sequenceLength`.
     */
    private fun Array<String?>.fill(charCode: Int, categoryCode: String): Boolean {
        val index = charCode % sequenceLength
        val current = this[index]
        if (current == null || (isPeriodic && current == categoryCode)) {
            this[index] = categoryCode
            return true
        }
        return false
    }

    override fun toString(): String {
        return "Bag{" +
                "sequenceLength=" + sequenceLength +
                ", isPeriodic=" + isPeriodic +
                ", unassignedCategoryCode=" + unassignedCategoryCode +
                ", categoryCodes=" + categoryCodes.contentToString() +
                "}"
    }
}


/**
 * A range of consequent chars that starts with a letter and ends with a letter, and contains multiple ranges of consequent not-letter chars.
 *
 * All letter chars in this range have the same category code.
 *
 * @param charCode the start of this range
 * @param categoryCode the category code of the char with the specified [charCode]
 * @param unassignedCategoryCode the categoryCode of the unassigned chars.
 *      Chars that are not appended or prepended are considered to be unassigned
 * @param makeCategory the function used to transform this range to an Int representation that is returned from the [category] function.
 */
internal class GapRangePattern private constructor(
    charCode: Int,
    private val categoryCode: String,
    private val unassignedCategoryCode: String,
    private val makeCategory: (start: Int, end: Int, gaps: List<Gap>) -> Int
) : CategorizedRangePattern {
    private val start: Int = charCode
    private var end: Int = charCode
    private val gaps = mutableListOf<Gap>()

    init {
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
            end = charCode
            return true
        }

        val newGap = Gap(start = end + 1, length = charCode - end - 1)
        val charsBeforeNewGap = newGap.start - if (gaps.isEmpty()) start else gaps.last().let { it.start + it.length }
        val bits = (gaps.size + 1) * (CHARS_BITS + GAP_BITS)

        if (isValid(charsBeforeNewGap, newGap.length) && bits <= TOTAL_BITS) {
            gaps.add(newGap)
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
        return makeCategory(start, end, gaps)
    }

    override fun categoryCodeOf(charCode: Int): String {
        require(charCode in start..end)
        for (gap in gaps) {
            if (charCode < gap.start) {
                return categoryCode
            }
            if (charCode < gap.start + gap.length) {
                return unassignedCategoryCode
            }
        }
        return categoryCode
    }

    override fun toString(): String {
        return "GapPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", length=" + rangeLength() +
                ", gaps=" + gaps +
                ", categoryCode=" + categoryCode +
                "}"
    }

    companion object {
        internal const val CHARS_BITS = 7
        internal const val GAP_BITS = 7
        private const val TOTAL_BITS = 31

        internal data class Gap(val start: Int, val length: Int)

        fun from(
            range: CategorizedRangePattern,
            charCode: Int,
            categoryCode: String,
            unassignedCategoryCode: String,
            makeCategory: (start: Int, end: Int, gaps: List<Gap>) -> Int
        ): CategorizedRangePattern? {
            val start = range.rangeStart()
            val startCategoryCode = range.categoryCodeOf(start)

            check(startCategoryCode != unassignedCategoryCode)

            if (startCategoryCode != categoryCode || categoryCode != "OL") return null

            val gapRange = GapRangePattern(start, startCategoryCode, unassignedCategoryCode, makeCategory)
            if (gapRange.append(start + 1, range.rangeEnd(), range::categoryCodeOf, charCode, categoryCode)) {
                return gapRange
            }
            return null
        }

        private fun isValid(charsBeforeGap: Int, gapLength: Int): Boolean {
            return charsBeforeGap < (1 shl CHARS_BITS) && gapLength < (1 shl GAP_BITS)
        }
    }
}


private fun CategorizedRangePattern.append(rangeStart: Int, rangeEnd: Int, categoryCodeOf: (Int) -> String, charCode: Int, categoryCode: String): Boolean {
    for (code in rangeStart..rangeEnd) {
        if (!append(code, categoryCodeOf(code))) {
            return false
        }
    }
    if (!append(charCode, categoryCode)) {
        return false
    }
    return true
}

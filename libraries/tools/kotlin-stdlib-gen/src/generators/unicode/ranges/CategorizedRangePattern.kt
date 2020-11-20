/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges

import generators.unicode.*

internal interface CategorizedRangePattern {
    fun append(charCode: Int, categoryCode: String): CategorizedRangePattern?
    fun rangeStart(): Int
    fun rangeEnd(): Int
    fun category(): Int
    fun categoryCodeOf(charCode: Int): String
}

// 17 and 31 category values are not reserved. Use 17 to replace UNASSIGNED value (0) to be able to encode range pattern categories.
internal const val UNASSIGNED_CATEGORY_VALUE_REPLACEMENT = 17
private val categoryCodeToValue = CharCategory.values().associateBy({ it.code }, { if (it.value == 0) UNASSIGNED_CATEGORY_VALUE_REPLACEMENT else it.value })

/**
 * A range of consequent chars having the same category
 */
internal class CategorizedConsequentPattern(
    val start: Int,
    val categoryCode: String
) : CategorizedRangePattern {
    var end = start
        private set

    init {
        require(categoryCode != "Cn") // Consequent ranges of UNASSIGNED category shouldn't be created.
    }

    fun setEnd(end: Int) {
        assert(this.end < end)
        this.end = end
    }

    override fun append(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        if (charCode == end + 1 && categoryCode == this.categoryCode) {
            end = charCode
            return this
        }
        return CategorizedAlternatingPattern.from(this, charCode, categoryCode)
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        return categoryCodeToValue[categoryCode]!!
    }

    override fun categoryCodeOf(charCode: Int): String {
        if (charCode !in start..end) {
            throw IllegalArgumentException("Char code ${charCode.hex()} is not in $this")
        }
        return categoryCode
    }

    override fun toString(): String {
        return "CategorizedConsequentPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", categoryCode=" + categoryCode +
                "}"
    }
}

/**
 * A range of consequent chars having alternating categories, e.g., [Lu, Ll, Lu, Ll, ...].
 */
internal class CategorizedAlternatingPattern private constructor(
    private val start: Int,
    private var end: Int,
    internal val evenOddCategoryCodes: Array<String>
) : CategorizedRangePattern {
    override fun append(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        if (charCode == end + 1 && categoryCode == evenOddCategoryCodes[charCode % 2]) {
            end = charCode
            return this
        }
        return CategorizedPeriodicTrioPattern.from(this, charCode, categoryCode)
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        // Each category value is <= 30, thus 5 bits is enough to represent it. Use 10 bits to encode 2 categories.
        val odd = categoryCodeToValue[evenOddCategoryCodes[1]]!!
        val even = categoryCodeToValue[evenOddCategoryCodes[0]]!!
        check(odd != 0)
        return (odd shl 5) or even
    }

    override fun categoryCodeOf(charCode: Int): String {
        if (charCode !in start..end) {
            throw IllegalArgumentException("Char code ${charCode.hex()} is not in $this")
        }
        return evenOddCategoryCodes[charCode % 2]
    }

    override fun toString(): String {
        return "CategorizedAlternatingPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", evenOddCategoryCodes=" + evenOddCategoryCodes.contentToString() +
                "}"
    }

    companion object {
        fun from(range: CategorizedRangePattern, charCode: Int, categoryCode: String): CategorizedRangePattern? {
            val evenOddCategoryCodes = arrayOf("", "")
            if (evenOddCategoryCodes.fill({ it % 2 }, range, charCode, categoryCode)) {
                return CategorizedAlternatingPattern(range.rangeStart(), charCode, evenOddCategoryCodes)
            }
            return CategorizedPeriodicTrioPattern.from(range, charCode, categoryCode)
        }
    }
}

/**
 * A range of consequent chars having alternating categories, e.g., [Lu, Ll, Lu, Ll, ...].
 */
internal class CategorizedPeriodicTrioPattern private constructor(
    private val start: Int,
    private var end: Int,
    private val categoryCodes: Array<String>
) : CategorizedRangePattern {
    override fun append(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        if (charCode == end + 1 && categoryCode == categoryCodes[charCode % 3]) {
            end = charCode
            return this
        }
        return null
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        // Each category value is <= 30, thus 5 bits is enough to represent it. Use 15 bits to encode 3 categories.
        val two = categoryCodeToValue[categoryCodes[2]]!!
        val one = categoryCodeToValue[categoryCodes[1]]!!
        val zero = categoryCodeToValue[categoryCodes[0]]!!
        check(two != 0)
        return (two shl 10) or (one shl 5) or zero
    }

    override fun categoryCodeOf(charCode: Int): String {
        if (charCode !in start..end) {
            throw IllegalArgumentException("Char code ${charCode.hex()} is not in $this")
        }
        return categoryCodes[charCode % 3]
    }

    override fun toString(): String {
        return "CategorizedTrioPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", categoryCodes=" + categoryCodes.contentToString() +
                "}"
    }

    companion object {
        fun from(range: CategorizedRangePattern, charCode: Int, categoryCode: String): CategorizedPeriodicTrioPattern? {
            val categoryCodes = arrayOf("", "", "")
            if (categoryCodes.fill({ it % 3 }, range, charCode, categoryCode)) {
                return CategorizedPeriodicTrioPattern(range.rangeStart(), charCode, categoryCodes)
            }
            return null
        }
    }
}

private fun Array<String>.fill(
    indexOfCharCode: (Int) -> Int,
    range: CategorizedRangePattern,
    charCode: Int,
    categoryCode: String
): Boolean {
    fun fill(charCode: Int, categoryCode: String): Boolean {
        val expected = this[indexOfCharCode(charCode)]
        if (expected.isNotEmpty() && expected != categoryCode) return false
        this[indexOfCharCode(charCode)] = categoryCode
        return true
    }

    for (ch in range.rangeStart()..range.rangeEnd()) {
        if (!fill(ch, range.categoryCodeOf(ch))) return false
    }
    for (ch in range.rangeEnd() + 1 until charCode) {
        if (!fill(ch, CharCategory.UNASSIGNED.code)) return false
    }
    if (!fill(charCode, categoryCode)) return false

    return true
}
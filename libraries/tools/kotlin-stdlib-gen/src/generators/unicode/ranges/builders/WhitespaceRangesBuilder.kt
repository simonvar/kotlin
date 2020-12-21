/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges.builders

import generators.unicode.ranges.patterns.CategorizedRangePattern

internal class WhitespaceRangesBuilder : RangesBuilder() {

    init {
        // Cc CONTROL spaces
        append("0009", "<Space, First>", WS)
        append("000D", "<Space, Last>", WS)
        append("001C", "<Space, First>", WS)
        append("001F", "<Space, Last>", WS)
    }

    override fun categoryId(categoryCode: String): String {
        return if (categoryCode in whitespaceCategories) WS else categoryCode
    }

    override fun shouldSkip(categoryId: String): Boolean {
        return categoryId != WS
    }

    override val makeOnePeriodCategory: (Array<String>) -> Int
        get() = { 0 }

    override fun evolveLastRange(lastRange: CategorizedRangePattern, charCode: Int, categoryId: String): CategorizedRangePattern? {
        return null
    }
}

private const val WS = "WS"

private val whitespaceCategories = listOf(
    CharCategory.SPACE_SEPARATOR.code,
    CharCategory.LINE_SEPARATOR.code,
    CharCategory.PARAGRAPH_SEPARATOR.code
)
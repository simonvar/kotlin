/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode

import templates.KotlinTarget
import java.io.FileWriter

internal sealed class RangesWritingStrategy {
    abstract val indentation: String
    abstract val rangesVisibilityModifier: String
    abstract fun beforeWritingRanges(writer: FileWriter)
    abstract fun afterWritingRanges(writer: FileWriter)
    abstract fun rangeReference(name: String): String

    companion object {
        fun of(target: KotlinTarget, wrapperName: String): RangesWritingStrategy {
            return when (target) {
                KotlinTarget.JS, KotlinTarget.JS_IR -> JsRangesWritingStrategy(wrapperName)
                else -> NativeRangesWritingStrategy
            }
        }
    }
}

internal object NativeRangesWritingStrategy : RangesWritingStrategy() {
    override val indentation: String get() = ""
    override val rangesVisibilityModifier: String get() = "private"
    override fun beforeWritingRanges(writer: FileWriter) {}
    override fun afterWritingRanges(writer: FileWriter) {}
    override fun rangeReference(name: String): String = name
}

// see KT-42461, KT-40482
internal class JsRangesWritingStrategy(private val wrapperName: String) : RangesWritingStrategy() {
    override val indentation: String get() = "        " // 8 spaces
    override val rangesVisibilityModifier: String get() = "internal"

    override fun beforeWritingRanges(writer: FileWriter) {
        writer.appendLine("private class $wrapperName {")
        writer.appendLine("    companion object {")
    }

    override fun afterWritingRanges(writer: FileWriter) {
        writer.appendLine("    }")
        writer.appendLine("}")
    }

    override fun rangeReference(name: String): String = "$wrapperName.$name"
}
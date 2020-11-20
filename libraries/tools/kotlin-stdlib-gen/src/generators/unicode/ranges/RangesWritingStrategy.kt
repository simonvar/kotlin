/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges

import generators.unicode.*
import templates.KotlinTarget
import java.io.FileWriter

internal sealed class RangesWritingStrategy {
    abstract val indentation: String
    abstract val rangesVisibilityModifier: String
    abstract fun beforeWritingRanges(writer: FileWriter)
    abstract fun afterWritingRanges(writer: FileWriter)
    abstract fun rangeReference(name: String): String

    abstract val useBase64: Boolean
    abstract fun writeRange(name: String, elements: List<Int>, writer: FileWriter)

    companion object {
        fun of(target: KotlinTarget, wrapperName: String, useBase64: Boolean = false): RangesWritingStrategy {
            return when (target) {
                KotlinTarget.JS, KotlinTarget.JS_IR -> JsRangesWritingStrategy(wrapperName, useBase64)
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

    override val useBase64: Boolean get() = false

    override fun writeRange(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeIntArray(name, elements, this)
}

// see KT-42461, KT-40482
internal class JsRangesWritingStrategy(
    private val wrapperName: String,
    override val useBase64: Boolean
) : RangesWritingStrategy() {
    override val indentation: String get() = "        " // 8 spaces
    override val rangesVisibilityModifier: String get() = "internal"

    override fun beforeWritingRanges(writer: FileWriter) {
        writer.appendLine("private class $wrapperName {")
        writer.appendLine("    companion object {")
        if (useBase64) {
            writer.appendLine("${indentation}private const val toBase64 = \"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\"")
            writer.appendLine("${indentation}internal val fromBase64 = IntArray(128).apply { toBase64.forEachIndexed { index, char -> this[char.toInt()] = index } }")
            writer.appendLine()
        }
    }

    override fun afterWritingRanges(writer: FileWriter) {
        writer.appendLine("    }")
        writer.appendLine("}")
    }

    override fun rangeReference(name: String): String = "$wrapperName.$name"

    override fun writeRange(name: String, elements: List<Int>, writer: FileWriter) =
        if (useBase64)
            writer.writeIntsInBase64(name, elements, this)
        else
            writer.writeIntArray(name, elements, this)
}
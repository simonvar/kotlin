/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types

import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.render
import kotlin.reflect.KClass

class CustomTypeAttribute(val annotations: List<FirAnnotationCall>) : ConeAttribute<CustomTypeAttribute>() {
    override fun union(other: CustomTypeAttribute?): CustomTypeAttribute? = null

    override fun intersect(other: CustomTypeAttribute?): CustomTypeAttribute? = null

    override fun isSubtypeOf(other: CustomTypeAttribute?): Boolean = false

    override fun toString(): String = annotations.joinToString(separator = " ") { it.render() }

    override val key: KClass<out CustomTypeAttribute>
        get() = CustomTypeAttribute::class
}

private val ConeAttributes.custom: CustomTypeAttribute? by ConeAttributes.attributeAccessor<CustomTypeAttribute>()

val ConeAttributes.customAnnotations: List<FirAnnotationCall> get() = custom?.annotations.orEmpty()

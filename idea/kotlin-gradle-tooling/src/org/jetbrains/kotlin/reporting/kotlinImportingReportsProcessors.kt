/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.reporting

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger

interface KotlinImportingReportsProcessor<T> {
    fun process(report: T)
}

interface SingleKotlinImportingReportProcessor<T : KotlinImportingReport> : KotlinImportingReportsProcessor<T>

abstract class GradleLogImportingReportProcessor<T : KotlinImportingReport>(protected val logger: Logger) :
    SingleKotlinImportingReportProcessor<T> {
    protected open val logLevel = LogLevel.INFO
    abstract fun logReport(report: T)
    override fun process(report: T) = logReport(report)
}

class GradleLogOrphanSourceSetReportProcessor(logger: Logger) : GradleLogImportingReportProcessor<OrphanSourceSetsImportingReport>(logger) {
    override val logLevel: LogLevel = LogLevel.WARN

    override fun logReport(report: OrphanSourceSetsImportingReport) {
        logger.log(logLevel, report.message)
    }
}

class GradleLogErroneousImportingReportProcessor(logger: Logger) : GradleLogImportingReportProcessor<ErroneousImportingReport>(logger) {
    override fun logReport(report: ErroneousImportingReport) {
        logger.log(logLevel, report.cause, report.throwable)
    }
}
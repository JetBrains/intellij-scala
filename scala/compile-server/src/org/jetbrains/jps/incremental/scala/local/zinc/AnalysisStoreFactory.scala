package org.jetbrains.jps.incremental.scala.local.zinc

import sbt.internal.inc.MixedAnalyzingCompiler
import xsbti.compile.AnalysisStore

import java.nio.file.Path

object AnalysisStoreFactory {
  def createAnalysisStore(analysisFile: Path): AnalysisStore =
    MixedAnalyzingCompiler.staticCachedStore(analysisFile, useTextAnalysis = false)
}

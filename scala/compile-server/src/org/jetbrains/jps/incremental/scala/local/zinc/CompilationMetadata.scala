package org.jetbrains.jps.incremental.scala
package local
package zinc

import sbt.internal.inc.Analysis
import xsbti.compile.{AnalysisStore, MiniSetup}

case class CompilationMetadata(previousAnalysis: Analysis, previousSetup: Option[MiniSetup])

object CompilationMetadata {

  def load(localStore: AnalysisStore): CompilationMetadata = {
    val analysisFromLocalStore = localStore.get()

    val (localAnalysis, localSetup) = if (analysisFromLocalStore.isPresent) {
      val content = analysisFromLocalStore.get()
      (content.getAnalysis.asInstanceOf[Analysis], Some(content.getMiniSetup))
    } else (Analysis.Empty, None)

    CompilationMetadata(localAnalysis, localSetup)
  }
}

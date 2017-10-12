package org.jetbrains.jps.incremental.scala.local.zinc

import java.io.File
import java.util.Optional

import org.jetbrains.jps.incremental.scala.data.CompilationData
import org.jetbrains.jps.incremental.scala.local.DefinesClassCache
import sbt.internal.inc.Analysis
import xsbti.compile.{AnalysisStore, CompileAnalysis, DefinesClass, PerClasspathEntryLookup}
import Utils._


case class IntellijEntryLookup(compilationData: CompilationData, fileToStore: File => AnalysisStore)
  extends PerClasspathEntryLookup {

  def loadAnalysis(forCpEntry: File): Option[Analysis] = {
    val cache = compilationData.outputToCacheMap.get(forCpEntry)
    val anaysisStore = cache.map(fileToStore)

    def readFromStore(store: AnalysisStore) = {
      val results = store.get()
      if (results.isPresent) results.get().getAnalysis.asInstanceOf[Analysis] else Analysis.Empty
    }

    anaysisStore.map(readFromStore)
  }

  private case class AnalysisBaseDefinesClass(analysis: Analysis) extends DefinesClass {
    override def apply(s: String): Boolean = analysis.relations.productClassName.reverse(s).nonEmpty
  }

  override def analysis(classpathEntry: File): Optional[CompileAnalysis] = {
    val loaded: Option[CompileAnalysis] = loadAnalysis(classpathEntry)
    loaded.toOptional
  }

  override def definesClass(classpathEntry: File): DefinesClass = {
    val analysisBasedDefine = loadAnalysis(classpathEntry).map(AnalysisBaseDefinesClass)
    analysisBasedDefine.getOrElse(DefinesClassCache.definesClassFor(classpathEntry))
  }
}

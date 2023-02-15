package org.jetbrains.jps.incremental.scala

import java.io.File

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.compiler.data.CompilationData

import scala.util.Try


object CachedCompilationService {
  /**
    * Content should always be (CompileAnalysis, MiniSetup).
    * Since we need this to work with old and new zinc I cannot use it explicitly (since packages changed)
    * Once we verify that we can use scala plugin with newer zinc version can change it to proper type.
    */
  type AnalysisContent = Any
  /**
    * Content should always be xsbti.compile.CompileResult.
    * Since we need this to work with old and new zinc I cannot use it explicitly (since packages changed)
    * Once we verify that we can use scala plugin with newer zinc version can change it to proper type.
    */
  type CompilationResults = Any
}


case class CacheResult(@Nls description: String, content: Option[CachedCompilationService.AnalysisContent])

trait ZincLogFilter {
  def shouldLog(serverity: MessageKind, msg: String): Boolean
}

abstract class CachedCompilationService {
  /** Create CachedCompilationProvider that will be used for compilation of particular scope of given configuration. */
  def createProvider(compilationData: CompilationData): Option[CachedCompilationProvider]
}

/** Represents changes in classfiles done during compilation */
trait ClassfilesChanges {
  /** All deleted classfiles split into compilation phases. Same file may be present in multiple phases */
  def deletedDuringCompilation(): Seq[Array[File]]
  /** All generated classfiles split into compilation phases. Same file may be present in multiple phases */
  def generatedDuringCompilation(): Seq[Array[File]]
}

/** Represents picked cache stats */
case class CacheStats(description: String, loadingDurationMillis: Long, loadingEnd: Long, isCached: Boolean)

/**
  * Decide if given build should use cache. First provided non empty result will be used in compilation.
  */
abstract class CachedCompilationProvider {
  /** Called before compilation to obtain compilation metatdata from other source. */
  def loadCache(current: Option[CachedCompilationService.AnalysisContent]): CacheResult

  /** Called when compilation finishes regardless if succesfull or nor  */
  def compilationFinished(compilationResults: Try[CachedCompilationService.CompilationResults],
                          classfilesChanges: ClassfilesChanges,
                          cacheStats: CacheStats): Unit

  def zincLogFilter(): Option[ZincLogFilter]
}
package org.jetbrains.jps.incremental.scala
package local
package zinc

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.compiler.data.CompilationData
import sbt.internal.inc.Analysis
import xsbti.compile.{AnalysisContents, AnalysisStore, CompileResult, MiniSetup}

import java.io.{PrintWriter, StringWriter}
import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Try
import scala.util.control.NonFatal

case class CompilationMetadata(previousAnalysis: Analysis,
                               previousSetup: Option[MiniSetup],
                               cacheDetails: CacheStats
                              )(providers: Seq[CachedCompilationProvider]) {
  val zincLogFilter: ZincLogFilter = {
    val filters = providers.flatMap(_.zincLogFilter())
    (severity, msg) => filters.forall(_.shouldLog(severity, msg))
  }

  def compilationFinished(compilationData: CompilationData,
                          result: Try[CompileResult],
                          classfilesChanges: ClassfilesChanges,
                          cacheStats: CacheStats): Unit =
    providers.foreach(_.compilationFinished(result, classfilesChanges, cacheStats))
}

object CompilationMetadata {

  private val cachedCompilationServices: List[CachedCompilationService] =
    ServiceLoader.load(classOf[CachedCompilationService])
      .iterator().asScala.toList

  def load(localStore: AnalysisStore, client: Client, compilationData: CompilationData): CompilationMetadata = {
    val analysisFromLocalStore = localStore.get()

    val cacheLoadingStart = System.currentTimeMillis()
    val cacheProviders = cachedCompilationServices.flatMap(_.createProvider(compilationData))

    def cacheStats(@Nls description: String, isCached: Boolean) = {
      val cacheLoadingEnd = System.currentTimeMillis()
      CacheStats(description, cacheLoadingEnd - cacheLoadingStart, cacheLoadingEnd, isCached)
    }

    def notUseCache(@Nls description: String) = {
      val (localAnalysis, localSetup) = if (analysisFromLocalStore.isPresent) {
        val content = analysisFromLocalStore.get()
        (content.getAnalysis.asInstanceOf[Analysis], Some(content.getMiniSetup))
      } else (Analysis.Empty, None)

      CompilationMetadata(localAnalysis, localSetup, cacheStats(description, isCached = false))(cacheProviders)
    }

    def loaderErrored(e: Throwable) = {
      val logs = new StringWriter()
      e.printStackTrace(new PrintWriter(logs))
      e.printStackTrace(System.out) // It will become a warning
      CacheResult(CompileServerBundle.message("exception.when.loading.cache.logs", logs), None)
    }

    val cachedResults: List[CacheResult] = cacheProviders.map{
      provider =>
        try provider.loadCache(analysisFromLocalStore.toScala) catch {
          case NonFatal(e) =>
            loaderErrored(e)
          case e: ClassNotFoundException =>
            loaderErrored(e)
          case e: NoClassDefFoundError =>
            loaderErrored(e)
        }
    }
    // In case all caches are empty use we will get description from first one
    val cacheToUse = cachedResults.find(_.content.nonEmpty) orElse cachedResults.headOption

    cacheToUse match {
      case Some(CacheResult(description, result)) =>
        result match {
          case Some(content: AnalysisContents) =>
            CompilationMetadata(content.getAnalysis.asInstanceOf[Analysis], Some(content.getMiniSetup), cacheStats(description, isCached = true))(cacheProviders)
          case Some(badFormat: AnyRef) =>
            val cacheResultClass = badFormat.getClass.getName
            client.warning(CompileServerBundle.message("unrecognized.cache.format", badFormat, cacheResultClass))
            notUseCache(CompileServerBundle.message("no.cache", s"badFormat ($cacheResultClass): $description)"))

          case _ =>
            notUseCache(CompileServerBundle.message("no.cache", description))
        }
      case _ =>
        notUseCache(CompileServerBundle.message("no.cache.found"))
    }
  }
}

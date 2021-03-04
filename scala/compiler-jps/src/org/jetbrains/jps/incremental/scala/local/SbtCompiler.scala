package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.util.Optional

import org.jetbrains.jps.incremental.scala.JpsBundle
import org.jetbrains.jps.incremental.scala.local.zinc.Utils._
import org.jetbrains.jps.incremental.scala.local.zinc.{BinaryToSource, _}
import org.jetbrains.plugins.scala.compiler.CompileOrder
import org.jetbrains.plugins.scala.compiler.data.CompilationData
import sbt.internal.inc._
import xsbti.VirtualFile
import xsbti.compile._

import scala.util.Try

/**
  * @author Pavel Fatin
  */
class SbtCompiler(javaTools: JavaTools, optScalac: Option[ScalaCompiler], fileToStore: File => AnalysisStore) extends AbstractCompiler {

  override def compile(compilationData: CompilationData, client: Client): Unit = optScalac match {
    case Some(scalac) => doCompile(compilationData, client, scalac)
    case _ =>
      client.error(JpsBundle.message("no.scalac.found.to.compile.scala.sources", compilationData.sources.take(10).mkString("\n")))
  }

  private def doCompile(compilationData: CompilationData, client: Client, scalac: ScalaCompiler): Unit = {
    client.progress(JpsBundle.message("loading.cached.results"))

    val incrementalCompiler = new IncrementalCompilerImpl

    val order = compilationData.order match {
      case CompileOrder.Mixed => xsbti.compile.CompileOrder.Mixed
      case CompileOrder.JavaThenScala => xsbti.compile.CompileOrder.JavaThenScala
      case CompileOrder.ScalaThenJava => xsbti.compile.CompileOrder.ScalaThenJava
    }

    val analysisStore = fileToStore(compilationData.cacheFile)
    val zincMetadata = CompilationMetadata.load(analysisStore, client, compilationData)
    import zincMetadata._

    client.progress(JpsBundle.message("searching.for.changed.files"))

    val progress = getProgress(client, compilationData.sources.size)
    val reporter = getReporter(client)
    val logger = getLogger(client, zincLogFilter)

    val intellijLookup = IntellijExternalLookup(compilationData, client, cacheDetails.isCached)
    val intellijClassfileManager = new IntellijClassfileManager

    DefinesClassCache.invalidateCacheIfRequired(compilationData.zincData.compilationStartDate)

    val incOptions = IncOptions.of()
      .withExternalHooks(IntelljExternalHooks(intellijLookup, intellijClassfileManager))
      .withRecompileOnMacroDef(Optional.of(boolean2Boolean(false)))
      .withTransitiveStep(5) // Default 3 was not enough for us

    val compilers = incrementalCompiler.compilers(javaTools, scalac)
    val setup = incrementalCompiler.setup(
      IntellijEntryLookup(compilationData, fileToStore),
      skip = false,
      compilationData.cacheFile.toPath,
      CompilerCache.fresh,
      incOptions,
      reporter,
      Option(progress),
      None,
      Array.empty)
    val previousResult = PreviousResult.create(
      Optional.of(previousAnalysis: CompileAnalysis),
      previousSetup.toOptional
    )
    val inputs = incrementalCompiler.inputs(
      compilationData.classpath.toArray.map(file => Utils.virtualFileConverter.toVirtualFile(file.toPath)),
      compilationData.zincData.allSources.toArray.map(file => Utils.virtualFileConverter.toVirtualFile(file.toPath)),
      compilationData.output.toPath,
      None,
      compilationData.scalaOptions.toArray,
      compilationData.javaOptions.toArray,
      100,
      Array.empty,
      order,
      compilers,
      setup,
      previousResult,
      Optional.empty(),
      virtualFileConverter,
      null
    )

    val compilationResult = Try {
      client.progress(JpsBundle.message("collecting.incremental.compiler.data"))
      val result: CompileResult = incrementalCompiler.compile(inputs, logger)

      if (result.hasModified || cacheDetails.isCached) {
        analysisStore.set(AnalysisContents.create(result.analysis(), result.setup()))

        intellijClassfileManager.deletedDuringCompilation().foreach(_.foreach(client.deleted))

        val binaryToSource = BinaryToSource(result.analysis, compilationData)

        def processGeneratedFile(classFile: File): Unit = {
          for (source <- binaryToSource.classfileToSources(classFile))
            client.generated(source, classFile, binaryToSource.className(classFile))
        }

        intellijClassfileManager.generatedDuringCompilation().flatten.foreach(processGeneratedFile)

        if (cacheDetails.isCached)
          previousAnalysis.stamps.allProducts.foreach { virtualFileRef =>
            processGeneratedFile(virtualFileConverter.toPath(virtualFileRef).toFile)
          }
      }
      result
    }

    compilationResult.recover {
      case e: CompileFailed =>
        // The error should be already handled via the `reporter`
        // However we need to invalidate source from last compilation
        val sourcesForInvalidation: Iterable[File] =
          if (intellijClassfileManager.deletedDuringCompilation().isEmpty) compilationData.sources
          else BinaryToSource(previousAnalysis, compilationData)
            .classfilesToSources(intellijClassfileManager.deletedDuringCompilation().last) ++ compilationData.sources

        sourcesForInvalidation.foreach(source => client.sourceStarted(source.getAbsolutePath))
      case e: Throwable =>
        // Invalidate analysis
        previousSetup.foreach(previous => analysisStore.set( AnalysisContents.create(Analysis.empty, previous)))

        // Keep files dirty
        compilationData.zincData.allSources.foreach(source => client.sourceStarted(source.getAbsolutePath))

        val msg = JpsBundle.message("compilation.failed.when.compiling", compilationData.output, e.getMessage, e.getStackTrace.mkString("\n  "))
        client.error(msg, None)
    }

    zincMetadata.compilationFinished(compilationData, compilationResult, intellijClassfileManager, cacheDetails)
  }
}

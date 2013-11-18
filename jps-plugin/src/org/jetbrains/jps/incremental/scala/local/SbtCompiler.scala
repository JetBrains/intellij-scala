package org.jetbrains.jps.incremental.scala
package local

import data.CompilationData
import model.Order
import sbt.compiler._
import java.io.File
import sbt.{CompileSetup, CompileOptions}
import xsbti.compile.CompileOrder
import sbt.inc.{IncOptions, Analysis, AnalysisStore, Locate}

/**
 * @author Pavel Fatin
 */
class SbtCompiler(javac: JavaCompiler, scalac: Option[AnalyzingCompiler], fileToStore: File => AnalysisStore) extends AbstractCompiler {
  def compile(compilationData: CompilationData, client: Client) {

    client.progress("Searching for changed files...")

    val compileSetup = {
      val output = CompileOutput(compilationData.output)
      val options = new CompileOptions(compilationData.scalaOptions, compilationData.javaOptions)
      val compilerVersion = scalac.map(_.scalaInstance.version).getOrElse("none")
      val order = compilationData.order match {
        case Order.Mixed => CompileOrder.Mixed
        case Order.JavaThenScala => CompileOrder.JavaThenScala
        case Order.ScalaThenJava => CompileOrder.ScalaThenJava
      }
      new CompileSetup(output, options, compilerVersion, order)
    }

    val compile = new AggressiveCompile(compilationData.cacheFile)

    val analysisStore = fileToStore(compilationData.cacheFile)

    val progress = getProgress(client)
    val reporter = getReporter(client)
    val logger = getLogger(client)

    val outputToAnalysisMap = compilationData.outputToCacheMap.map { case (output, cache) =>
      val analysis = fileToStore(cache).get().map(_._1).getOrElse(Analysis.Empty)
      (output, analysis)
    }

    try {
      compile.compile1(
        compilationData.sources,
        compilationData.classpath,
        compileSetup,
        Some(progress),
        analysisStore,
        outputToAnalysisMap.get,
        Locate.definesClass,
        scalac.orNull,
        javac,
        reporter,
        skip = false,
        CompilerCache.fresh,
        IncOptions.Default
      )(logger)
    } catch {
      case _: xsbti.CompileFailed => // the error should be already handled via the `reporter`
    }
  }
}
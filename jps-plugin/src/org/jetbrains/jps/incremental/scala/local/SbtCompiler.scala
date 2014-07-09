package org.jetbrains.jps.incremental.scala
package local

import data.CompilationData
import sbt.compiler._
import java.io.File
import sbt.{CompileSetup, CompileOptions}
import sbt.inc.{IncOptions, Analysis, AnalysisStore, Locate}
import org.jetbrains.plugin.scala.compiler.{NameHashing, CompileOrder}

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
        case CompileOrder.Mixed => xsbti.compile.CompileOrder.Mixed
        case CompileOrder.JavaThenScala => xsbti.compile.CompileOrder.JavaThenScala
        case CompileOrder.ScalaThenJava => xsbti.compile.CompileOrder.ScalaThenJava
      }
      val nameHashingValue = compilationData.nameHashing match {
        case NameHashing.DEFAULT => IncOptions.Default.nameHashing
        case NameHashing.ENABLED => true
        case NameHashing.DISABLED => false
      }
      new CompileSetup(output, options, compilerVersion, order, nameHashingValue)
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
        IncOptions.Default.withNameHashing(compileSetup.nameHashing)
      )(logger)
    } catch {
      case _: xsbti.CompileFailed => // the error should be already handled via the `reporter`
    }
  }
}
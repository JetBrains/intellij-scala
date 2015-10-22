package org.jetbrains.jps.incremental.scala
package local

import java.io.File

import org.jetbrains.jps.incremental.scala.data.CompilationData
import org.jetbrains.jps.incremental.scala.model.CompileOrder
import org.jetbrains.plugin.scala.compiler.NameHashing
import sbt.compiler.IC.Result
import sbt.compiler._
import sbt.inc.{Analysis, AnalysisStore, IncOptions, Locate}

/**
 * @author Pavel Fatin
 */
class SbtCompiler(javac: JavaCompiler, scalac: Option[AnalyzingCompiler], fileToStore: File => AnalysisStore) extends AbstractCompiler {
  def compile(compilationData: CompilationData, client: Client) {

    client.progress("Searching for changed files...")

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

    val compileOutput = CompileOutput(compilationData.output)

    val analysisStore = fileToStore(compilationData.cacheFile)
    val (previousAnalysis, previousSetup) = {
      analysisStore.get().map {
        case (a, s) => (a, Some(s))
      } getOrElse {
        (Analysis.Empty, None)
      }
    }

    val progress = getProgress(client)
    val reporter = getReporter(client)
    val logger = getLogger(client)

    val outputToAnalysisMap = compilationData.outputToCacheMap.map { case (output, cache) =>
      val analysis = fileToStore(cache).get().map(_._1).getOrElse(Analysis.Empty)
      (output, analysis)
    }

    try {
      val Result(analysis, setup, hasModified) = IC.incrementalCompile(
        scalac.orNull,
        javac,
        compilationData.sources,
        compilationData.classpath,
        compileOutput,
        CompilerCache.fresh,
        Some(progress),
        compilationData.scalaOptions,
        compilationData.javaOptions,
        previousAnalysis,
        previousSetup,
        outputToAnalysisMap.get,
        Locate.definesClass,
        reporter,
        order,
        skip = false,
        IncOptions.Default.withNameHashing(nameHashingValue)
      )(logger)

      analysisStore.set(analysis, setup)

    } catch {
      case _: xsbti.CompileFailed => // the error should be already handled via the `reporter`
    }
  }
}
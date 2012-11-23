package org.jetbrains.jps.incremental.scala

import data.CompilationData
import model.Order
import sbt.compiler._
import java.io.File
import sbt.{CompileSetup, CompileOptions}
import xsbti.compile.{CompileProgress, CompileOrder}
import org.jetbrains.jps.incremental.messages.CompilerMessage
import sbt.inc.{Analysis, AnalysisStore, Locate}
import scala.Some
import org.jetbrains.jps.incremental.MessageHandler
import xsbti.{Severity, Position, AnalysisCallback}
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import xsbti.api.SourceAPI

/**
 * @author Pavel Fatin
 */
class CompilerImpl(scalac: AnalyzingCompiler,
                   javac: JavaCompiler,
                   storeProvider: File => AnalysisStore) extends Compiler {

  def compile(compilationData: CompilationData,
              messageHandler: MessageHandler,
              fileHandler: FileHandler,
              progress: CompileProgress) {

    val compileSetup = {
      val output = CompileOutput(compilationData.output)
      val options = new CompileOptions(compilationData.options, Nil)
      val compilerVersion = scalac.scalaInstance.version
      val order = compilationData.order match {
        case Order.JavaThenScala => CompileOrder.ScalaThenJava
        case Order.ScalaThenJava => CompileOrder.JavaThenScala
      }
      new CompileSetup(output, options, compilerVersion, order)
    }

    val compile = new AggressiveCompile(compilationData.cacheFile)

    val analysisStore = storeProvider(compilationData.cacheFile)

    val callback = new Callback("scala", messageHandler, fileHandler)

    val reporter = new ProblemReporter("scala", messageHandler)

    val logger = new MessageHandlerLogger("scala", messageHandler)

    val outputToAnalysisMap = compilationData.outputToCacheMap.map { case (output, cache) =>
      val analysis = storeProvider(cache).get().map(_._1).getOrElse(Analysis.Empty)
      (output, analysis)
    }

    compile.compile1(
      compilationData.sources,
      compilationData.classpath,
      compileSetup,
      Some(progress),
      analysisStore,
      outputToAnalysisMap.get,
      Locate.definesClass,
      scalac,
      javac,
      reporter,
      false,
      CompilerCache.fresh,
      Some(callback)
    )(logger)
  }
}

private class Callback(compilerName: String,
                       messageHandler: MessageHandler,
                       fileHandler: FileHandler) extends AnalysisCallback {

  def beginSource(source: File) {}

  def sourceDependency(dependsOn: File, source: File) {}

  def binaryDependency(binary: File, name: String, source: File) {}

  def generatedClass(source: File, module: File, name: String) {
    messageHandler.processMessage(new CompilerMessage(compilerName, Kind.PROGRESS, "Generated " + module.getPath))
    fileHandler.processFile(source, module)
  }

  def endSource(sourcePath: File) {}

  def api(sourceFile: File, source: SourceAPI) {}

  def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean) {}
}


package org.jetbrains.jps.incremental.scala

import sbt.compiler._
import java.io.File
import sbt.{CompileSetup, CompileOptions}
import xsbti.compile.{CompileProgress, CompileOrder}
import org.jetbrains.jps.incremental.messages.{CompilerMessage, ProgressMessage}
import sbt.inc.{AnalysisStore, Locate}
import scala.Some
import org.jetbrains.jps.incremental.MessageHandler
import xsbti.{Severity, Position, AnalysisCallback}
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import xsbti.api.SourceAPI

/**
 * @author Pavel Fatin
 */
class CompilerImpl(scalac: AnalyzingCompiler, javac: JavaCompiler, storeProvider: File => AnalysisStore) extends Compiler {

  def compile(sources: Seq[File], classpath: Seq[File], options: Seq[String], output: File, scalaFirst: Boolean, cacheFile: File,
              messageHandler: MessageHandler, fileHandler: FileHandler, progress: CompileProgress) {

    val compileSetup =
      new CompileSetup(CompileOutput(output),
        new CompileOptions(options, Nil),
        scalac.scalaInstance.version,
        if (scalaFirst) CompileOrder.ScalaThenJava else CompileOrder.JavaThenScala)

    val compile = new AggressiveCompile(cacheFile)

    messageHandler.processMessage(new ProgressMessage("Compiling..."))

    val analysisStore = storeProvider(cacheFile)

    val callback = new Callback("scala", messageHandler, fileHandler)

    val reporter = new ProblemReporter("scala", messageHandler)

    val logger = new MessageHandlerLogger("scala", messageHandler)

    compile.compile1(sources, classpath, compileSetup, Some(progress), analysisStore, Function.const(None), Locate.definesClass,
      scalac, javac, reporter, false, CompilerCache.fresh, Some(callback))(logger)
  }
}

private class Callback(compilerName: String, messageHandler: MessageHandler, fileHandler: FileHandler) extends AnalysisCallback {
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


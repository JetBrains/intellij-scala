package org.jetbrains.jps.incremental.scala

import data.CompilationData
import model.Order
import sbt.compiler._
import java.io.File
import sbt.{CompileSetup, CompileOptions}
import xsbti.compile.{CompileProgress, CompileOrder}
import sbt.inc.{Analysis, AnalysisStore, Locate}
import xsbti._
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import xsbti.api.SourceAPI

/**
 * @author Pavel Fatin
 */
class CompilerImpl(scalac: AnalyzingCompiler, javac: JavaCompiler, fileToStore: File => AnalysisStore) extends Compiler {
  def compile(compilationData: CompilationData, client: Client) {
    val compileSetup = {
      val output = CompileOutput(compilationData.output)
      val options = new CompileOptions(compilationData.options, Nil)
      val compilerVersion = scalac.scalaInstance.version
      val order = compilationData.order match {
        case Order.JavaThenScala => CompileOrder.JavaThenScala
        case Order.ScalaThenJava => CompileOrder.Mixed
      }
      new CompileSetup(output, options, compilerVersion, order)
    }

    val compile = new AggressiveCompile(compilationData.cacheFile)

    val analysisStore = fileToStore(compilationData.cacheFile)

    val progress = new ClientProgress(client)

    val callback = new ClientCallback(client)

    val reporter = new ClientReporter(client)

    val logger = new ClientLogger(client)

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
        scalac,
        javac,
        reporter,
        false,
        CompilerCache.fresh,
        Some(callback)
      )(logger)
    } catch {
      case _: xsbti.CompileFailed => // the error should be already handled via the `reporter`
    }
  }
}

private class ClientProgress(client: Client) extends CompileProgress {
  def startUnit(phase: String, unitPath: String) {
    client.progress("Phase " + phase + " on " + unitPath)
  }

  def advance(current: Int, total: Int) = {
    client.progress("", Some(current.toFloat / total.toFloat))
    !client.isCanceled
  }
}

private class ClientReporter(client: Client) extends Reporter {
  private var entries: List[Problem] = Nil

  def reset() {
    entries = Nil
  }

  def hasErrors = entries.exists(_.severity == Severity.Error)

  def hasWarnings = entries.exists(_.severity == Severity.Warn)

  def printSummary() {}

  def problems = entries.reverse.toArray

  def log(pos: Position, msg: String, sev: Severity) {
    entries ::= new Problem {
      val category = ""
      val position = pos
      val message = msg
      val severity = sev
    }

    val kind = sev match {
      case Severity.Info => Kind.INFO
      case Severity.Warn => Kind.WARNING
      case Severity.Error => Kind.ERROR
    }

    val source = toOption(pos.sourcePath).map(new File(_))
    val line = toOption(pos.line).map(_.toLong)
    val column = toOption(pos.pointer).map(_.toLong + 1L)

    client.message(kind, msg, source, line, column)
  }

  def toOption[T](value: Maybe[T]): Option[T] = if (value.isDefined) Some(value.get) else None
}

private class ClientCallback(client: Client) extends AnalysisCallback {
  def beginSource(source: File) {}

  def sourceDependency(dependsOn: File, source: File) {}

  def binaryDependency(binary: File, name: String, source: File) {}

  def generatedClass(source: File, module: File, name: String) {
    client.progress("Generated " + module.getPath)
    client.generated(source, module)
  }

  def endSource(sourcePath: File) {}

  def api(sourceFile: File, source: SourceAPI) {}

  def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean) {}
}

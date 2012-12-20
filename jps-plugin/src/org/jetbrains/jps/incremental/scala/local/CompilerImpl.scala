package org.jetbrains.jps.incremental.scala
package local

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
import com.intellij.openapi.util.text.StringUtil

/**
 * @author Pavel Fatin
 */
class CompilerImpl(javac: JavaCompiler, scalac: Option[AnalyzingCompiler], fileToStore: File => AnalysisStore) extends Compiler {
  def compile(compilationData: CompilationData, client: Client) {
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

private class ClientLogger(client: Client) extends Logger {
  def error(msg: F0[String]) {
    client.error(msg())
  }

  def warn(msg: F0[String]) {
    client.warning(msg())
  }

  def info(msg: F0[String]) {
    //    client.info(msg())
    client.progress(msg())
  }

  def debug(msg: F0[String]) {
    //    client.info(msg())
  }

  def trace(exception: F0[Throwable]) {
    client.trace(exception())
  }
}

private class ClientProgress(client: Client) extends CompileProgress {
  def startUnit(phase: String, unitPath: String) {
    val unitName = new File(unitPath).getName
    client.progress("Phase " + phase + " on " + unitName)
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

    val messageWithLineAndPointer = {
      val indent = toOption(pos.pointerSpace)
      msg + "\n" + pos.lineContent + indent.map("\n" + _ + "^").getOrElse("")
    }

    client.message(kind, messageWithLineAndPointer, source, line, column)
  }

  def toOption[T](value: Maybe[T]): Option[T] = if (value.isDefined) Some(value.get) else None
}

private class ClientCallback(client: Client) extends AnalysisCallback {
  def beginSource(source: File) {}

  def sourceDependency(dependsOn: File, source: File) {}

  def binaryDependency(binary: File, name: String, source: File) {}

  def generatedClass(source: File, module: File, name: String) {
    // TODO remove this assertion later
    if (!module.exists) {
      client.error("Generated class file does not exist: " + module)
    }
    client.progress("Generated " + module.getName)
    client.generated(source, module, name)
  }

  def endSource(sourcePath: File) {}

  def api(sourceFile: File, source: SourceAPI) {}

  def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean) {}
}

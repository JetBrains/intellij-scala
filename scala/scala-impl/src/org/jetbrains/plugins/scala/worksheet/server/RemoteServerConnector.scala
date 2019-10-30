package org.jetbrains.plugins.scala
package worksheet.server

import java.io._

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory, CompilerPaths}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.Base64Converter
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.DummyClient
import org.jetbrains.plugins.scala.compiler.{NonServerRunner, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.worksheet.runconfiguration.ReplModeArgs
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector._
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetFileSettings, WorksheetProjectSettings}
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterRepl

import scala.util.control.NonFatal

// TODO: remove all deprecated Base64Converter usages
private[worksheet]
class RemoteServerConnector(
  module: Module,
  worksheetPsiFile: ScFile,
  worksheet: File,
  output: File,
  worksheetClassName: String,
  replArgs: Option[ReplModeArgs],
  needsCheck: Boolean
) extends RemoteServerConnectorBase(module, Seq(worksheet), output, needsCheck) {

  private val runType: WorksheetMakeType = WorksheetProjectSettings.getMakeType(module.getProject)

  override protected def compilerSettings: ScalaCompilerSettings =
    WorksheetCommonSettings(worksheetPsiFile).getCompilerProfile.getSettings

  /**
    * Args (for running in compile server process only)
    * 0. Compiled class name to execute
    * 1. Path to runners.jar (needed to load MacroPrinter for types)
    * 2. Output - path to temp file, where processed worksheet code is written
    * 3. Output dir for compiled worksheet (i.e. for compiled temp file with processed code)
    * 4. Original worksheet file path. Used as id of REPL session on compile server (iff REPL enabled)
    * 5. Code chunk to interpret (iff REPL enabled)
    * 6. "replenabled" - iff/if REPL mode enabled
    */
  override val worksheetArgs: Array[String] =
    runType match {
      case OutOfProcessServer =>
        Array.empty[String]
      case _ =>
        val base = Array(worksheetClassName, runnersJar.getAbsolutePath, output.getAbsolutePath) ++ outputDirs
        replArgs.map(ra => base ++ Array(ra.path, ra.codeChunk, "replenabled")).getOrElse(base)
    }

  // TODO: make something more advanced than just `callback: Runnable`: error reporting, Future, Task, etc...
  // TODO: add logging across all these callbacks in RunWorksheetAction, WorksheetCompiler, RemoteServerConnector...
  // NOTE: for now this method is non-blocking for runType == NonServer and blocking for other run types
  def compileAndRun(originalFile: VirtualFile,
                    consumer: RemoteServerConnector.CompilerInterface,
                    callback: RemoteServerConnectorResult => Unit): Unit = {

    val project = module.getProject
    val worksheetHook = WorksheetFileHook.instance(project)

    val client = new MyTranslatingClient(project, originalFile, consumer)

    val process = try {
      val worksheetProcess = runType match {
        case InProcessServer | OutOfProcessServer =>
          val runner = new RemoteServerRunner(project)
          val argumentsFinal = arguments
          runner.buildProcess(argumentsFinal, client)

        case NonServer =>
          val argumentsFinal = "NO_TOKEN" +: arguments
          val argumentsEncoded = argumentsFinal.map { arg =>
            val argFixed = if(arg.isEmpty) "#STUB#" else arg
            Base64Converter.encode(argFixed.getBytes("UTF-8"))
          }
          val runner = new NonServerRunner(project)
          runner.buildProcess(argumentsEncoded, client)
      }

      if (worksheetProcess == null) {
        callback(RemoteServerConnectorResult.ProcessTerminatedError(ExitCode.ABORT))
        return
      }

      val psiFile = inReadAction {
        PsiManager.getInstance(project).findFile(originalFile)
      }
      val fileToReHighlight = psiFile match {
        case scalaFile: ScalaFile if WorksheetFileSettings.isRepl(scalaFile) => Some(scalaFile)
        case _ => None
      }

      worksheetHook.disableRun(originalFile, Some(worksheetProcess))
      worksheetProcess.addTerminationCallback { exception =>
        worksheetHook.enableRun(originalFile, consumer.isCompiledWithErrors)

        fileToReHighlight.foreach(WorksheetEditorPrinterRepl.rehighlight)

        val result = exception match {
          case Some(_) =>
            RemoteServerConnectorResult.ProcessTerminatedError(ExitCode.ABORT)
          case _ if consumer.isCompiledWithErrors =>
            RemoteServerConnectorResult.CompilationError
          case _ =>
            runType match {
              case OutOfProcessServer => RemoteServerConnectorResult.Compiled(worksheetClassName, output)
              case _                  => RemoteServerConnectorResult.CompiledAndEvaluated
            }
        }

        callback.apply(result)
      }

      WorksheetProcessManager.add(originalFile, worksheetProcess)
      worksheetProcess
    } catch {
      case NonFatal(ex) =>
        callback(RemoteServerConnectorResult.UnknownError(ex))
        throw ex
    }

    // exceptions thrown inside the process should be propagated to callback via termination callback
    process.run()
  }

  private def outputDirs: Array[String] = {
    val modules = ModuleRootManager.getInstance(module).getDependencies :+ module
    modules.map(CompilerPaths.getModuleOutputPath(_, false))
  }
}

private[worksheet]
object RemoteServerConnector {

  sealed trait RemoteServerConnectorResult
  object RemoteServerConnectorResult {
    case class Compiled(worksheetClassName: String, outputDir: File) extends RemoteServerConnectorResult
    case object CompiledAndEvaluated extends RemoteServerConnectorResult

    trait Error extends RemoteServerConnectorResult
    case object CompilationError extends Error // assuming that errors are collected by CompilerInterface
    final case class ProcessTerminatedError(rc: ExitCode) extends Error
    final case class UnknownError(cause: Throwable) extends Error
  }

  private class MyTranslatingClient(project: Project, worksheet: VirtualFile, consumer: CompilerInterface) extends DummyClient {
    private val endMarker = WorksheetSourceProcessor.END_GENERATED_MARKER

    override def progress(text: String, done: Option[Float]): Unit =
      consumer.progress(text, done)

    override def trace(exception: Throwable): Unit =
      consumer.trace(exception)

    override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
      val lines = (if(text == null) "" else "").split("\n")
      val linesLength = lines.length

      val differ = if (linesLength > 2) {
        val endLineIdx = lines(linesLength - 2).indexOf(endMarker)
        if (endLineIdx != -1) {
          endLineIdx + endMarker.length
        } else 0
      } else 0

      val finalText = if (differ == 0) text else {
        val buffer = new StringBuilder

        for (j <- 0 until (linesLength - 2)) {
          buffer.append(lines(j)).append("\n")
        }

        val lines1 = lines(linesLength - 1)

        buffer
          .append(lines(linesLength - 2).substring(differ)).append("\n")
          .append(if (lines1.length > differ) lines1.substring(differ) else lines1).append("\n")

        buffer.toString()
      }

      // TODO: current line & column calculation are broken
      val line1 = line.map(i => i - 4).map(_.toInt).getOrElse(-1)
      val column1 = column.map(_ - differ).map(_.toInt).getOrElse(-1)

      val category = toCompilerMessageCategory(kind)

      val message = new CompilerMessageImpl(project, category, finalText, worksheet, line1, column1, null)
      consumer.message(message)
    }

    private def toCompilerMessageCategory(kind: Kind): CompilerMessageCategory = {
      import BuildMessage.Kind._
      kind match {
        case INFO | JPS_INFO | OTHER        => CompilerMessageCategory.INFORMATION
        case ERROR | INTERNAL_BUILDER_ERROR => CompilerMessageCategory.ERROR
        case PROGRESS                       => CompilerMessageCategory.STATISTICS
        case WARNING                        => CompilerMessageCategory.WARNING
      }
    }

    override def worksheetOutput(text: String): Unit =
      consumer.worksheetOutput(text)

    override def processingEnd(): Unit = {
      super.processingEnd()
      consumer.finish()
    }
  }

  // Worksheet Integration Tests rely on that this is the main entry point for all compiler messages
  trait CompilerMessagesConsumer {
    def message(message: CompilerMessage)
  }

  trait CompilerInterface extends CompilerMessagesConsumer {
    def progress(text: String, done: Option[Float])
    def finish()

    def worksheetOutput(text: String)
    def trace(thr: Throwable)

    def isCompiledWithErrors: Boolean
  }
}

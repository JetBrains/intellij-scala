package org.jetbrains.plugins.scala
package worksheet.server

import java.io._
import java.net._

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.progress.CompilerTask
import com.intellij.openapi.compiler.{CompilerMessageCategory, CompilerPaths}
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
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
import org.jetbrains.plugins.scala.extensions.ThrowableExt
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.worksheet.runconfiguration.ReplModeArgs
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.{MyTranslatingClient, OuterCompilerInterface, RemoteServerConnectorResult}
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetFileSettings, WorksheetProjectSettings}
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinter, WorksheetEditorPrinterRepl}

// TODO: remove all deprecated Base64Converter usages
final class RemoteServerConnector(
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
  def compileAndRun(originalFile: VirtualFile, consumer: OuterCompilerInterface, callback: RemoteServerConnectorResult => Unit): Unit = { // TODO: make it some normal errors

    val project = module.getProject
    val worksheetHook = WorksheetFileHook.instance(project)

    val client = new MyTranslatingClient(project, originalFile, consumer)

    try {
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
      worksheetProcess.addTerminationCallback {
        worksheetHook.enableRun(originalFile, client.isCompiledWithErrors)
        fileToReHighlight.foreach(WorksheetEditorPrinterRepl.rehighlight)

        val result = if (client.isCompiledWithErrors) {
          RemoteServerConnectorResult.CompilationError("compilation ended with errors")
        } else runType match {
          case OutOfProcessServer => RemoteServerConnectorResult.Compiled
          case _                  => RemoteServerConnectorResult.CompiledAndEvaluated
        }
        callback.apply(result)
      }

      WorksheetProcessManager.add(originalFile, worksheetProcess)

      worksheetProcess.run()
    } catch {
      case _: SocketException =>
        callback(RemoteServerConnectorResult.ProcessTerminatedError(ExitCode.ABORT)) // someone has stopped the server
    }
  }

  private def outputDirs: Array[String] = {
    val modules = ModuleRootManager.getInstance(module).getDependencies :+ module
    modules.map(CompilerPaths.getModuleOutputPath(_, false))
  }
}

object RemoteServerConnector {

  sealed trait RemoteServerConnectorResult
  object RemoteServerConnectorResult {
    object Compiled extends RemoteServerConnectorResult
    object CompiledAndEvaluated extends RemoteServerConnectorResult
    trait Error extends RemoteServerConnectorResult
    case class ProcessTerminatedError(rc: ExitCode) extends Error
    case class CompilationError(message: String) extends Error
  }

  private class MyTranslatingClient(project: Project, worksheet: VirtualFile, consumer: OuterCompilerInterface) extends DummyClient {
    private val length = WorksheetSourceProcessor.END_GENERATED_MARKER.length

    private var hasErrors = false

    def isCompiledWithErrors: Boolean = hasErrors

    override def progress(text: String, done: Option[Float]): Unit =
      consumer.progress(text, done)

    override def trace(exception: Throwable): Unit =
      consumer.trace(exception)

    override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
      val lines = (if(text == null) "" else "").split("\n")
      val linesLength = lines.length

      val differ = if (linesLength > 2) {
        val i = lines(linesLength - 2) indexOf WorksheetSourceProcessor.END_GENERATED_MARKER
        if (i > -1) i + length else 0
      } else 0

      val finalText = if (differ == 0) text else {
        val buffer = new StringBuilder

        for (j <- 0 until (linesLength - 2)) buffer append lines(j) append "\n"

        val lines1 = lines(linesLength - 1)

        buffer append lines(linesLength - 2).substring(differ) append "\n" append (
          if (lines1.length > differ) lines1.substring(differ) else lines1) append "\n"
        buffer.toString()
      }

      val line1 = line.map(i => i - 4).map(_.toInt)
      val column1 = column.map(_ + 1 - differ).map(_.toInt)

      import BuildMessage.Kind._

      val category = kind match {
        case INFO | JPS_INFO | OTHER =>
          CompilerMessageCategory.INFORMATION
        case ERROR =>
          hasErrors = true
          CompilerMessageCategory.ERROR
        case PROGRESS =>
          CompilerMessageCategory.STATISTICS
        case WARNING =>
          CompilerMessageCategory.WARNING
      }

      consumer.message(
        new CompilerMessageImpl(project, category, finalText, worksheet, line1 getOrElse -1, column1 getOrElse -1, null)
      )
    }

    override def worksheetOutput(text: String): Unit =
      consumer.worksheetOutput(text)
  }
  
  trait OuterCompilerInterface {
    def message(message: CompilerMessageImpl)
    def progress(text: String, done: Option[Float])
    
    def worksheetOutput(text: String)
    def trace(thr: Throwable)
  }
  
  class CompilerInterfaceImpl(task: CompilerTask,
                              worksheetPrinter: Option[WorksheetEditorPrinter],
                              indicator: Option[ProgressIndicator],
                              auto: Boolean = false)
    extends OuterCompilerInterface {

    override def progress(text: String, done: Option[Float]): Unit = {
      if (auto) return
      val taskIndicator = ProgressManager.getInstance().getProgressIndicator
      
      if (taskIndicator != null) {
        taskIndicator.setText(text)
        done.foreach(d => taskIndicator.setFraction(d.toDouble))
      }
    }

    override def message(message: CompilerMessageImpl): Unit = {
      if (auto) return
      task.addMessage(message)
    }

    override def worksheetOutput(text: String): Unit =
      worksheetPrinter.foreach(_.processLine(text))

    override def trace(thr: Throwable): Unit = {
      val message = "\n" + thr.stackTraceText // stacktrace already contains thr.toString which contains message
      worksheetPrinter.foreach(_.internalError(message))
    }
  }
}

package org.jetbrains.plugins.scala
package worksheet.processor

import java.io.File

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.progress.CompilerTask
import com.intellij.notification.NotificationType
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import WorksheetCompiler.WorksheetEvaluationError._
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetRunResult.{CompileOutsideCompilerProcess, Done}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.{WorksheetEvaluationError, WorksheetRunResult}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.{ReplModeArgs, WorksheetCache}
import org.jetbrains.plugins.scala.worksheet.server._
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType.WorksheetPreprocessError
import org.jetbrains.plugins.scala.worksheet.settings._
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinter

class WorksheetCompiler(
  module: Module,
  editor: Editor,
  worksheetFile: ScalaFile,
  callback: Either[WorksheetEvaluationError, WorksheetRunResult] => Unit,
  auto: Boolean
) {
  import worksheet.processor.WorksheetCompiler._

  private implicit val project: Project = worksheetFile.getProject

  private val makeType = WorksheetProjectSettings.getMakeType(project)
  private val runType = WorksheetFileSettings.getRunType(worksheetFile)
  private val worksheetVirtual = worksheetFile.getVirtualFile

  private def showErrorNotification(msg: String): Unit =
    NotificationUtil.builder(project, msg)
      .setGroup("Scala")
      .setNotificationType(NotificationType.ERROR)
      .setTitle(ConfigErrorHeader)
      .show()

  private def createCompilerTask: CompilerTask =
    new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)

  private def createWorksheetPrinter: Option[WorksheetEditorPrinter] =
    Option(runType.createPrinter(editor, worksheetFile))

  private def runCompilerTask(className: String, code: String, tempFile: File, outputDir: File): Unit = {
    val afterCompileCallback: Either[String, Unit] => Unit = {
      case Right(_) =>
        val result = makeType match {
          case OutOfProcessServer => CompileOutsideCompilerProcess(className, outputDir.getAbsolutePath)
          case _                  => Done()
        }
        callback(Right(result))
      case Left(value) =>
        callback(Left(UnknownError(value)))
    }

    val task = createCompilerTask
    val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, createWorksheetPrinter, None, auto)

    FileUtil.writeToFile(tempFile, code)

    val compileWork: Runnable = () => try {
      val connector = new RemoteServerConnector(module, worksheetFile, tempFile, outputDir, className, None, true)
      connector.compileAndRun(afterCompileCallback, worksheetVirtual, consumer)
    } catch {
      case ex: IllegalArgumentException =>
        showErrorNotification(ex.getMessage)
    }
    task.start(compileWork, EmptyRunnable)

    if (WorksheetFileSettings.shouldShowReplWarning(worksheetFile)) {
      val message = "Worksheet can be executed in REPL mode only in compile server process."
      task.addMessage(new CompilerMessageImpl(project, CompilerMessageCategory.WARNING, message))
    }
  }

  private def runDumbTask(replModeArgs: ReplModeArgs): Unit = {
    val afterCompileCallback: Either[String, Unit] => Unit = {
      case Right(_)    => callback(Right(Done()))
      case Left(value) => callback(Left(UnknownError(value)))
    }
    val task = createCompilerTask

    val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, createWorksheetPrinter, None)
    val compileWork: Runnable = () => {
      val connector = new RemoteServerConnector(module, worksheetFile, new File(""), new File(""), "", Some(replModeArgs), false)
      connector.compileAndRun(afterCompileCallback, worksheetVirtual, consumer)
    }
    task.start(compileWork, EmptyRunnable)
  }

  private def compileAndRunCode(request: WorksheetCompileRunRequest): Unit = {
    WorksheetCompilerUtil.removeOldMessageContent(project) //or not?

    makeType match {
      case InProcessServer | OutOfProcessServer =>
        CompileServerLauncher.ensureServerRunning(project)
      case NonServer =>
    }

    request match {
      case RunRepl(code) =>
        val args = ReplModeArgs(worksheetVirtual.getCanonicalPath, code)
        runDumbTask(args)

      case RunCompile(code, name) =>
        val cache = WorksheetCache.getInstance(project)
        val (_, tempFile, outputDir) = cache.updateOrCreateCompilationInfo(worksheetVirtual.getCanonicalPath, worksheetFile.getName)
        cache.removePrinter(editor)
        runCompilerTask(name, code, tempFile, outputDir)
    }
  }

  def compileAndRunFile(): Unit =
    runType.process(worksheetFile, editor) match {
      case Right(request) =>
        compileAndRunCode(request)
      case Left(preprocessError) =>
        showCompilationError(preprocessError)
        callback(Left(PreprocessError(preprocessError)))
    }

  private def showCompilationError(error: WorksheetPreprocessError): Unit = {
    WorksheetCompilerUtil.showCompilationError(
      worksheetVirtual, error.position, Array(error.message),
      () => editor.getCaretModel.moveToLogicalPosition(error.position)
    )
  }
}

object WorksheetCompiler extends WorksheetPerFileConfig {

  private val EmptyRunnable: Runnable = () => {}
  private val ConfigErrorHeader = "Worksheet configuration error:"

  sealed trait WorksheetRunResult
  object WorksheetRunResult {
    case class Done() extends WorksheetRunResult
    case class CompileOutsideCompilerProcess(name: String, addToClasspath: String) extends WorksheetRunResult
  }

  sealed trait WorksheetEvaluationError
  object WorksheetEvaluationError {
    case class PreprocessError(error: WorksheetPreprocessError) extends WorksheetEvaluationError
    case class UnknownError(reason: Any) extends WorksheetEvaluationError // TODO: make more specific errors
  }
}

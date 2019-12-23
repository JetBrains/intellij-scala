package org.jetbrains.plugins.scala
package worksheet.processor

import java.io.File

import com.intellij.compiler.progress.CompilerTask
import com.intellij.notification.{Notification, NotificationAction, NotificationType}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompileServerManager, ScalaCompileServerForm, ScalaCompileServerSettings}
import org.jetbrains.plugins.scala.extensions.ThrowableExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.EvaluationCallback
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.{ReplModeArgs, WorksheetCache}
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.{CompilerInterface, CompilerMessagesConsumer, RemoteServerConnectorResult}
import org.jetbrains.plugins.scala.worksheet.server._
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType.WorksheetPreprocessError
import org.jetbrains.plugins.scala.worksheet.settings._
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinter, WorksheetEditorPrinterRepl}

import scala.collection.mutable

private[worksheet]
class WorksheetCompiler(
  module: Module,
  editor: Editor,
  worksheetFile: ScalaFile,
  originalCallback: EvaluationCallback,
  auto: Boolean
) {
  import worksheet.processor.WorksheetCompiler._

  private implicit val project: Project = worksheetFile.getProject

  private val runType = WorksheetFileSettings.getRunType(worksheetFile)
  private val makeType = WorksheetCompiler.getMakeType(project, runType)
  private val worksheetVirtual = worksheetFile.getVirtualFile

  private def showErrorNotification(msg: String): Unit =
    errorNotification(msg).show()

  private def errorNotification(msg: String): NotificationUtil.NotificationBuilder = {
    NotificationUtil.builder(project, msg)
      .setGroup("Scala")
      .setNotificationType(NotificationType.ERROR)
      .setTitle(ConfigErrorHeader)
  }

  private def createCompilerTask: CompilerTask =
    new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)

  private def createWorksheetPrinter: WorksheetEditorPrinter =
    runType.createPrinter(editor, worksheetFile)

  private def runPlainCompilerTask(className: String, code: String, tempFile: File, outputDir: File)
                                  (task: CompilerTask,
                                   afterCompileCallback: RemoteServerConnectorResult => Unit,
                                   consumer: RemoteServerConnector.CompilerInterface): Unit = {
    FileUtil.writeToFile(tempFile, code)

    val compileWork: Runnable = () => try {
      val connector = new RemoteServerConnector(module, worksheetFile, tempFile, outputDir, className, None, makeType, true)
      connector.compileAndRun(worksheetVirtual, consumer, afterCompileCallback)
    } catch {
      case ex: IllegalArgumentException =>
        showErrorNotification(ex.getMessage)
        afterCompileCallback(RemoteServerConnectorResult.UnknownError(ex))
    }
    task.start(compileWork, EmptyRunnable)
  }

  private def runReplCompilerTask(replModeArgs: ReplModeArgs)
                                 (task: CompilerTask,
                                  afterCompileCallback: RemoteServerConnectorResult => Unit,
                                  consumer: RemoteServerConnector.CompilerInterface): Unit = {
    val compileWork: Runnable = () => try {
      val connector = new RemoteServerConnector(module, worksheetFile, new File(""), new File(""), "", Some(replModeArgs), makeType, false)
      connector.compileAndRun(worksheetVirtual, consumer, afterCompileCallback)
    } catch {
      case ex: IllegalArgumentException =>
        showErrorNotification(ex.getMessage)
        afterCompileCallback(RemoteServerConnectorResult.UnknownError(ex))
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

    val task = createCompilerTask
    val printer = createWorksheetPrinter
    // do not show error messages in Plain mode on auto-run
    val consumer = new CompilerInterfaceImpl(task, printer, auto && request.isInstanceOf[RunCompile])
    printer match {
      case replPrinter: WorksheetEditorPrinterRepl =>
        replPrinter.updateMessagesConsumer(consumer)
      case _ =>
    }

    // TODO: this is needed to close the timer of printer in one place
    //  the solution is quite ugly when we use raw callbacks, consider using some lightweight streaming/reactive library
    val callback: EvaluationCallback = result => {
      if (!consumer.isCompiledWithErrors) {
        printer.flushBuffer()
      }
      printer.close()
      originalCallback(result)
    }

    val afterCompileCallback: RemoteServerConnectorResult => Unit = {
      case RemoteServerConnectorResult.Compiled(className, outputDir) =>
        if (runType.isReplRunType) {
          val error = new AssertionError("Worksheet is expected to be evaluated in REPL mode")
          callback(WorksheetCompilerResult.EvaluationError(error))
        } else {
          WorksheetCompilerLocalEvaluator.executeWorksheet(worksheetFile.getName, worksheetFile, className, outputDir.getAbsolutePath)(callback, printer)(module)
        }
      case RemoteServerConnectorResult.CompiledAndEvaluated =>
        callback(WorksheetCompilerResult.CompiledAndEvaluated)
      case RemoteServerConnectorResult.CompilationError =>
        callback(WorksheetCompilerResult.CompilationError)
      case error: RemoteServerConnectorResult.Error =>
        callback(WorksheetCompilerResult.RemoteServerConnectorError(error))
    }

    val cache = WorksheetCache.getInstance(project)
    if (ApplicationManager.getApplication.isUnitTestMode) {
      cache.addCompilerMessagesCollector(editor, consumer)
    }

    request match {
      case RunRepl(code) =>
        val args = ReplModeArgs(worksheetVirtual.getCanonicalPath, code)
        runReplCompilerTask(args)(task, afterCompileCallback, consumer)

      case RunCompile(code, name) =>
        val (_, tempFile, outputDir) = cache.updateOrCreateCompilationInfo(worksheetVirtual.getCanonicalPath, worksheetFile.getName)
        runPlainCompilerTask(name, code, tempFile, outputDir)(task, afterCompileCallback, consumer)
    }
  }

  private def showReplRequiresCompileServerNotification(message: String): Unit =
    errorNotification(message)
      .removeTitle()
      .addAction(new NotificationAction("Enable compile server") {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
          notification.expire()
          CompileServerManager.enableCompileServer(project)
        }
      })
      .addAction(new NotificationAction("Configure compile server") {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
          notification.expire()
          val filter = ScalaCompileServerForm.SearchFilter.USE_COMPILE_SERVER_FOR_SCALA
          CompileServerManager.showCompileServerSettingsDialog(project, filter)
        }
      })
      .show()

  def compileAndRunFile(): Unit = {
    if (runType.isReplRunType && makeType != InProcessServer) {
      val error = WorksheetCompilerResult.PreconditionError("Worksheet in REPL mode can only be executed in compile server process")
      showReplRequiresCompileServerNotification(error.message)
      originalCallback(error)
    } else if (DumbService.getInstance(project).isDumb) {
      originalCallback(WorksheetCompilerResult.ProjectIsInDumbState)
    } else {
      runType.process(worksheetFile, editor) match {
        case Right(request) =>
          compileAndRunCode(request)

        case Left(error@WorksheetPreprocessError(message, position)) =>
          showCompilationError(message, position)
          originalCallback(WorksheetCompilerResult.PreprocessError(error))
      }
    }
  }

  private def showCompilationError(message: String, position: LogicalPosition): Unit = {
    WorksheetCompilerUtil.showCompilationError(
      worksheetVirtual, position, Array(message),
      () => editor.getCaretModel.moveToLogicalPosition(position)
    )
  }
}

private[worksheet]
object WorksheetCompiler extends WorksheetPerFileConfig {

  private val EmptyRunnable: Runnable = () => {}
  private val ConfigErrorHeader = "Worksheet configuration error"

  type EvaluationCallback = WorksheetCompilerResult => Unit

  sealed trait WorksheetCompilerResult
  object WorksheetCompilerResult {
    // worksheet was compiled without any errors (but warnings possible) and evaluated successfully
    object CompiledAndEvaluated extends WorksheetCompilerResult

    sealed trait WorksheetCompilerError extends WorksheetCompilerResult
    final case class PreprocessError(error: WorksheetPreprocessError) extends WorksheetCompilerError
    final case class PreconditionError(message: String) extends WorksheetCompilerError
    final case object CompilationError extends WorksheetCompilerError
    final case class EvaluationError(cause: Throwable) extends WorksheetCompilerError
    final case class ProcessTerminatedError(returnCode: Int, message: String) extends WorksheetCompilerError
    final case class RemoteServerConnectorError(error: RemoteServerConnectorResult.Error) extends WorksheetCompilerError
    final case object ProjectIsInDumbState extends WorksheetCompilerError
  }

  trait CompilerMessagesCollector {
    def collectedMessages: Seq[CompilerMessage]
  }

  class CompilerInterfaceImpl(
    task: CompilerTask,
    worksheetPrinter: WorksheetEditorPrinter,
    auto: Boolean
  ) extends CompilerInterface
    with CompilerMessagesConsumer
    with CompilerMessagesCollector {

    private val messages = mutable.ArrayBuffer[CompilerMessage]()
    private var hasCompilationErrors = false

    private val isUnitTestMode = ApplicationManager.getApplication.isUnitTestMode

    override def isCompiledWithErrors: Boolean = hasCompilationErrors

    override def collectedMessages: Seq[CompilerMessage] = messages

    override def message(message: CompilerMessage): Unit = {
      // for now we only need the compiler messages in unit tests
      if (message.getCategory == CompilerMessageCategory.ERROR) {
        hasCompilationErrors = true
      }
      if (!auto) {
        task.addMessage(message)
        if (isUnitTestMode) {
          messages += message
        }
      }
    }

    override def progress(text: String, done: Option[Float]): Unit = {
      if (auto) return
      val taskIndicator = ProgressManager.getInstance.getProgressIndicator

      if (taskIndicator != null) {
        taskIndicator.setText(text)
        done.foreach(d => taskIndicator.setFraction(d.toDouble))
      }
    }

    override def worksheetOutput(text: String): Unit =
      worksheetPrinter.processLine(text)

    override def trace(ex: Throwable): Unit = {
      val message = "\n" + ex.stackTraceText // stacktrace already contains thr.toString which contains message
      worksheetPrinter.internalError(message)
    }
  }

  private def getMakeType(project: Project, runType: WorksheetExternalRunType): WorksheetMakeType =
    if (ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED) {
      if (ScalaProjectSettings.getInstance(project).isInProcessMode || runType == WorksheetExternalRunType.ReplRunType) {
        InProcessServer
      } else {
        OutOfProcessServer
      }
    } else {
      NonServer
    }
}

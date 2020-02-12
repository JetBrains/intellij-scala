package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.notification.{Notification, NotificationAction, NotificationType}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.{CompileServerManager, ScalaCompileServerForm}
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.{Precondition, WorksheetCompilerError}
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.RemoteServerConnectorResult

class WorksheetCompilerErrorReporter(
  project: Project,
  worksheetFile: VirtualFile,
  worksheetEditor: Editor,
  log: Logger
) {

  private val ConfigErrorHeader = ScalaBundle.message("worksheet.configuration.errors.base")

  def reportError(error: WorksheetCompilerError): Unit = error match {
    case WorksheetCompilerResult.PreprocessError(error)            => showCompilationError(error.message, error.position)
    case WorksheetCompilerResult.PreconditionError(precondition)   => showReplRequiresCompileServerNotification(preconditionMessage(precondition))
    case WorksheetCompilerResult.UnknownError(exception)           => reportUnexpectedError(exception)
    case WorksheetCompilerResult.CompilationError                  => // assuming that compilation errors are already reported by CompilerTask in WorksheetCompiler
    case WorksheetCompilerResult.ProcessTerminatedError(_, _)      => // not handled, used to cancel evaluation
    case WorksheetCompilerResult.RemoteServerConnectorError(error) =>
      error match {
        case RemoteServerConnectorResult.ExpectedError(exception)   => showConfigErrorNotification(exception.getMessage)
        case RemoteServerConnectorResult.UnexpectedError(exception) => reportUnexpectedError(exception)
        case RemoteServerConnectorResult.ProcessTerminatedError(_)  => // not handled, used to cancel evaluation
      }
  }

  private def reportUnexpectedError(exception: Throwable): Unit =
    log.error("Unexpected error occurred during worksheet evaluation", exception)

  private def preconditionMessage(precondition: Precondition): String = precondition match {
    case Precondition.ReplRequiresCompileServerProcess => ScalaBundle.message("worksheet.configuration.errors.repl.is.available.only.in.compile.server.process")
  }

  private def showConfigErrorNotification(msg: String): Unit = {
    if (project.isDisposed) return
    configErrorNotification(msg).show()
  }

  private def showReplRequiresCompileServerNotification(message: String): Unit =
    configErrorNotification(message)
      .removeTitle()
      .addAction(new NotificationAction(ScalaBundle.message("worksheet.configuration.errors.enable.compile.server")) {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
          notification.expire()
          CompileServerManager.enableCompileServer(project)
        }
      })
      .addAction(new NotificationAction((ScalaBundle.message("worksheet.configuration.errors.configure.compile.server"))) {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
          notification.expire()
          val filter = ScalaCompileServerForm.SearchFilter.USE_COMPILE_SERVER_FOR_SCALA
          CompileServerManager.showCompileServerSettingsDialog(project, filter)
        }
      })
      .show()

  private def configErrorNotification(msg: String): NotificationUtil.NotificationBuilder =
    NotificationUtil.builder(project, msg)
      .setGroup("Scala")
      .setNotificationType(NotificationType.ERROR)
      .setTitle(ConfigErrorHeader)

  private def showCompilationError(message: String, position: LogicalPosition): Unit =
    WorksheetCompilerUtil.showCompilationError(
      worksheetFile, position, Array(message),
      () => worksheetEditor.getCaretModel.moveToLogicalPosition(position)
    )(project)
}

package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.notification.{Notification, NotificationAction, NotificationType}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.compiler.{CompileServerManager, ScalaCompileServerForm}
import org.jetbrains.plugins.scala.console.configuration.ScalaSdkJLineFixer
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.{Precondition, WorksheetCompilerError}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetEvaluationErrorReporter.showConfigErrorNotification
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.RemoteServerConnectorResult
import org.jetbrains.plugins.scala.worksheet.utils.notifications.WorksheetNotificationsGroup

class WorksheetEvaluationErrorReporter(
  project: Project,
  worksheetFile: VirtualFile,
  worksheetEditor: Editor,
  log: Logger
) {

  //noinspection RedundantBlock
  def reportError(error: WorksheetCompilerError): Unit = {
    val WCR = WorksheetCompilerResult
    error match {
      case WCR.PreprocessError(error)            => showCompilationError(error.message, error.position)
      case WCR.PreconditionError(precondition)   =>
        precondition match {
          case Precondition.ReplRequiresCompileServerProcess =>
            showReplRequiresCompileServerNotification()
          case Precondition.ProjectShouldBeInSmartState =>
            WorksheetNotificationsGroup
              .createNotification(
                WorksheetBundle.message("worksheet.configuration.errors.project.indexing.not.finished"),
                NotificationType.WARNING
              )
              .notify(project)
        }
      case WCR.UnknownError(exception)           => reportUnexpectedError(exception)
      case WCR.CompilationError                  => // assuming that compilation errors are already reported by CompilerTask in WorksheetCompiler
      case WCR.ProcessTerminatedError(_, _)      => // not handled, used to cancel evaluation
      case WCR.CompileServerIsNotRunningError    => showConfigErrorNotification(project, WorksheetBundle.message("compile.server.is.not.running"))
      case WCR.ProjectIsAlreadyDisposed(name, trace)  =>
        log.error(s"Project `$name` is already disposed", trace)
      case WCR.RemoteServerConnectorError(error) =>
        val RSCR = RemoteServerConnectorResult
        error match {
          case RSCR.ExpectedError(exception)   =>
            //noinspection ReferencePassedToNls
            showConfigErrorNotification(project, exception.getMessage)
          case RSCR.UnexpectedError(exception) =>
            reportUnexpectedError(exception)
          case RSCR.CantInitializeProcessError => {
            // TODO
          }
          case RSCR.ProcessTerminatedError(_)  => {
            //don't report when worksheet evaluation stopped
            // (stop button currently stops entire compile server see SCL-17265)
          }
          case RSCR.RequiredJLineIsMissingFromClasspathError(module) =>
            ScalaSdkJLineFixer.showJLineMissingNotification(module, WorksheetBundle.message("worksheet.subsystem.name"))
        }
    }
  }

  private def reportUnexpectedError(exception: Throwable): Unit =
    log.error("Unexpected error occurred during worksheet evaluation", exception)

  private def showReplRequiresCompileServerNotification(): Unit = {
    WorksheetNotificationsGroup
      .createNotification(
        WorksheetBundle.message("worksheet.configuration.errors.repl.is.available.only.in.compile.server.process"),
        NotificationType.ERROR
      )
      .addAction(new NotificationAction(WorksheetBundle.message("worksheet.configuration.errors.enable.compile.server")) {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
          notification.expire()
          CompileServerManager.enableCompileServer()
        }
      })
      .addAction(new NotificationAction(WorksheetBundle.message("worksheet.configuration.errors.configure.compile.server")) {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
          notification.expire()
          val filter = ScalaCompileServerForm.SearchFilter.USE_COMPILE_SERVER_FOR_SCALA
          CompileServerManager.showCompileServerSettingsDialog(project, filter)
        }
      })
      .notify(project)
  }

  private def showCompilationError(message: String, position: LogicalPosition): Unit =
    WorksheetCompilerUtil.showCompilationError(
      worksheetFile, position, Array(message),
      () => worksheetEditor.getCaretModel.moveToLogicalPosition(position)
    )(project)
}

object WorksheetEvaluationErrorReporter {


  def showConfigErrorNotification(project: Project, @Nls msg: String): Unit = {
    if (project.isDisposed)
      return

    WorksheetNotificationsGroup
      .createNotification(
        WorksheetBundle.message("worksheet.configuration.errors.base"),
        msg,
        NotificationType.ERROR
      )
      .notify(project)
  }
}

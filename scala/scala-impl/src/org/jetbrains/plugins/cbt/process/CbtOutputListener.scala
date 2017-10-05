package org.jetbrains.plugins.cbt.process

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.CbtProjectSystem

import scala.collection.mutable

class CbtOutputListener(onOutput: (String, Boolean) => Unit,
                        projectOpt: Option[Project],
                        notificationSource: NotificationSource) {
  private val warningsList = mutable.ListBuffer.empty[String]
  private val errorsList = mutable.ListBuffer.empty[String]
  private val outBuffer = StringBuilder.newBuilder
  private val errorBuffer = StringBuilder.newBuilder
  private val errorBuilder = new ErrorWarningBuilder("[error]")
  private val warningBuilder = new ErrorWarningBuilder("[warn]")

  def parseLine(text: String, stderr: Boolean): Unit = {
    val escapedText = AnsiCodesEscaper.escape(text)
    if (stderr) {
      errorBuffer.append(escapedText)
      handleErrorOrWarning(escapedText)
    } else {
      outBuffer.append(escapedText)
    }
    onOutput(escapedText, stderr)
  }

  def stdErr: String =
    errorBuffer.toString

  def stdOut: String =
    outBuffer.toString

  def errors: Seq[String] =
    errorsList

  def warnngs: Seq[String] =
    warningsList

  private def handleErrorOrWarning(text: String): Unit = {
    if (isError(text))
      errorBuilder.parserPart(text).foreach { res =>
        errorsList += res
        projectOpt.foreach(CbtOutputListener.showError(_, res, notificationSource))
      }
    else if (isWarning(text))
      warningBuilder.parserPart(text).foreach { res =>
        warningsList += res
        projectOpt.foreach(CbtOutputListener.showWarning(_, res, notificationSource))
      }
  }

  private def isError(line: String) =
    line.startsWith("[error]")

  private def isWarning(line: String) =
    line.startsWith("[warn]")
}

private class ErrorWarningBuilder(prefix: String) {
  private val buffer = StringBuilder.newBuilder

  def parserPart(text: String): Option[String] = {
    val part = text.stripPrefix(prefix)
    buffer.append(part)
    if (part.trim == "^") {
      val fullText = buffer.toString
      buffer.clear()
      Some(fullText)
    } else None
  }
}


object CbtOutputListener {
  def showError(project: Project, text: String, notificationSource: NotificationSource): Unit = {
    showNotification(project, text, NotificationCategory.ERROR, notificationSource)
  }

  def showWarning(project: Project, text: String, notificationSource: NotificationSource): Unit = {
    showNotification(project, text, NotificationCategory.WARNING, notificationSource)
  }

  private def showNotification(project: Project,
                               text: String,
                               notificationCategory: NotificationCategory,
                               notificationSource: NotificationSource): Unit = {
    ApplicationManager.getApplication.invokeLater(runnable {
      val notification =
        new NotificationData("CBT project import",
          text,
          notificationCategory,
          notificationSource)
      ExternalSystemNotificationManager.getInstance(project)
        .showNotification(CbtProjectSystem.Id, notification)
    })
  }
}

object AnsiCodesEscaper {
  private val escapeCodes =
    Seq(Console.RESET,
      Console.RED,
      Console.GREEN,
      Console.BLUE,
      Console.YELLOW)

  def escape(text: String): String =
    escapeCodes.fold(text)((t, c) => t.replaceAllLiterally(c, ""))
}
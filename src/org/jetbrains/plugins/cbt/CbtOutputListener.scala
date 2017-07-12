package org.jetbrains.plugins.cbt

import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.cbt.project.CbtProjectSystem

import scala.collection.mutable

class CbtOutputListener(onOutput: (String, Boolean) => Unit,
                        projectOpt: Option[Project],
                        notificationSource: NotificationSource) {

  private val textAccumulator = notificationSource match {
    case NotificationSource.PROJECT_SYNC =>
      new ColoredOutputAccumulator(onStdErr)
    case NotificationSource.TASK_EXECUTION =>
      new OutputAccumulator(onStdErr)
  }

  private val warningsList = mutable.ListBuffer.empty[String]
  private val errorsList = mutable.ListBuffer.empty[String]
  private val outBuffer = StringBuilder.newBuilder
  private val errorBuffer = StringBuilder.newBuilder
  private val errorBuilder = new ErrorWarningBuilder("[error]")
  private val warningBuilder = new ErrorWarningBuilder("[warning]")

  def dataReceived(text: String, stderr: Boolean): Unit = {
    if (stderr) {
      textAccumulator.addText(text)
    } else {
      outBuffer.append(text)
      onOutput(text, false)
    }
  }

  def stdOut: String =
    outBuffer.toString

  def stdErr: String =
    errorBuffer.toString

  def errors: Seq[String] =
    errorsList

  def warnngs: Seq[String] =
    warningsList

  private def onStdErr(text: String): Unit = {
    errorBuffer.append(text + "\n")
    onOutput(text + "\n", true)
    if (isError(text)) {
      errorsList += text
      for {
        text <- errorBuilder.parserPart(text)
        project <- projectOpt
      } {
        CbtOutputListener.showError(project, text, notificationSource)
      }
    } else if (isWarning(text)) {
      warningsList += text
      for {
        text <- warningBuilder.parserPart(text)
        project <- projectOpt
      } {
        CbtOutputListener.showWarning(project, text, notificationSource)
      }
      projectOpt.foreach(CbtOutputListener.showWarning(_, text, notificationSource))
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
    buffer.append(part + "\n")
    if (part.trim == "^") {
      val fullText = buffer.toString
      buffer.clear()
      Some(fullText)
    } else {
      None
    }
  }
}

private class ColoredOutputAccumulator(onMessage: String => Unit) extends OutputAccumulator(onMessage) {
  private val colorEncoder = new AnsiEscapeDecoder

  private val textAcceptor = new ColoredTextAcceptor {
    override def coloredTextAvailable(text: String, attributes: Key[_]): Unit = {
      concatErrorMessages(text)
    }
  }

  override def addText(text: String): Unit =
    colorEncoder.escapeText(text, ProcessOutputTypes.STDERR, textAcceptor)
}

private class OutputAccumulator(onMessage: String => Unit) {
  private val messageBuffer = new StringBuilder

  def addText(text: String): Unit =
    concatErrorMessages(text)

  protected def concatErrorMessages(text: String): Unit =
    text.trim match {
      case "[" =>
        messageBuffer.append("[")
      case "]" =>
        messageBuffer.append("] ")
      case _ if messageBuffer.nonEmpty && messageBuffer.endsWith("[") =>
        messageBuffer.append(text.trim)
      case _ if messageBuffer.nonEmpty =>
        messageBuffer.append(text.rtrim)
        onMessage(messageBuffer.toString())
        messageBuffer.clear()
      case _ => onMessage(text.rtrim)
    }
}

object CbtOutputListener {

  def showError(project: Project, text: String, notificationSource: NotificationSource): Unit = {
    showNotification(project, text, NotificationCategory.ERROR, notificationSource)
  }

  def showNotification(project: Project, text: String, notificationCategory: NotificationCategory,
                       notificationSource: NotificationSource): Unit = {
    ApplicationManager.getApplication.invokeLater(new Runnable {
      override def run(): Unit = {
        val notification = new NotificationData("CBT project import", text,
          notificationCategory, notificationSource)
        ExternalSystemNotificationManager.getInstance(project).showNotification(CbtProjectSystem.Id, notification)
      }
    })
  }

  def showWarning(project: Project, text: String, notificationSource: NotificationSource): Unit = {
    showNotification(project, text, NotificationCategory.WARNING, notificationSource)
  }
}
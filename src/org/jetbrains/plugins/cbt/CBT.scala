package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.cbt.project.settings.CbtSystemSettings
import org.jetbrains.plugins.cbt.project.structure.CbtProjectImporingException
import org.jetbrains.sbt.RichVirtualFile

import scala.collection.mutable
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}


object CBT {
  def runAction(action: Seq[String], root: File): Try[String] =
    runAction(action, root, None)

  def buildInfoXml(root: File, extraModules: Seq[String],
                   taskListener: Option[(ExternalSystemTaskId, ExternalSystemTaskNotificationListener)]): Try[Elem] = {
    val extraMododulesStr = extraModules.mkString(":")
    val xml = runAction(Seq("buildInfoXml", extraMododulesStr), root, taskListener)
    xml.map(XML.loadString)
  }

  def runAction(action: Seq[String], root: File,
                taskListener: Option[(ExternalSystemTaskId, ExternalSystemTaskNotificationListener)]): Try[String] = {
    val colorEncoder = new AnsiEscapeDecoder

    val outBuffer = new StringBuilder
    val errorBuffer = new StringBuilder
    val errors = new mutable.ListBuffer[String]
    val messageBuffer = new StringBuilder

    val logger = ProcessLogger(
      { text =>
        outBuffer.append(text.trim :+ '\n')
      }, { text =>
        taskListener.foreach {
          case (id, l) =>
            colorEncoder.escapeText(text, ProcessOutputTypes.STDERR, new ColoredTextAcceptor {
              override def coloredTextAvailable(text: String, attributes: Key[_]): Unit = {
                def onMessage(message: String) = {
                  l.onStatusChange(new ExternalSystemTaskNotificationEvent(id, message + "\n"))
                  errorBuffer.append(message + "\n")
                  if (isError(message)) {
                    errors += message
                  }
                }
                concatErrorMessages(text, messageBuffer, errors, onMessage)
              }
            })
        }
      })
    val exitCode = Process(Seq("cbt") ++ action, root) ! logger
    exitCode match {
      case 0 =>
        Success(outBuffer.toString)
      case _ =>
        Failure(new CbtProjectImporingException(errors.mkString("\n")))
    }
  }

  private def isError(line: String) =
    line.startsWith("[error]")

  private def concatErrorMessages(message: String, messageBuffer: StringBuilder,
                                  errors: mutable.ListBuffer[String], onMessage: String => Unit) =
    message.trim match {
      case "[" =>
        messageBuffer.append("[")
      case "]" =>
        messageBuffer.append("] ")
      case _ if messageBuffer.nonEmpty && messageBuffer.endsWith("[") =>
        messageBuffer.append(message.trim)
      case _ if messageBuffer.nonEmpty =>
        messageBuffer.append(message.rtrim)
        onMessage(messageBuffer.toString())
        messageBuffer.clear()
      case _ => onMessage(message.rtrim)
    }

  def isCbtModuleDir(entry: VirtualFile): Boolean =
    entry.containsDirectory("build")

}

package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.plugins.cbt.project.structure.CbtProjectImporingException
import org.jetbrains.sbt.RichVirtualFile

import scala.collection.mutable
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}


object CBT {

  def buildInfoXml(root: File, settings: CbtExecutionSettings,
                   taskListener: Option[(ExternalSystemTaskId, ExternalSystemTaskNotificationListener)]): Try[Elem] = {
    def buildParams: Seq[String] = {
      val extraModulesStr = settings.extraModules.mkString(":")
      val needCbtLibsStr = settings.isCbt.unary_!.toString
      Seq("--extraModules", extraModulesStr, "--needCbtLibs", needCbtLibsStr)
    }
    val xml = runAction("buildInfoXml" +: buildParams, root, taskListener)
    xml.map(XML.loadString)
  }

  def runAction(action: Seq[String], root: File,
                taskListener: Option[(ExternalSystemTaskId, ExternalSystemTaskNotificationListener)]): Try[String] = {
    val outBuffer = new StringBuilder
    val errorBuffer = new StringBuilder

    val errors = new mutable.ListBuffer[String]
    val onMessage = (text: String) => {
      errorBuffer.append(text + "\n")
      if (isError(text)) {
        errors += text
      }
      taskListener.foreach {
        case (id, l) =>
          l.onStatusChange(new ExternalSystemTaskNotificationEvent(id, text + "\n"))
      }
    }

    val ansiCodesEscaper = new AnsiCodesEscaper(onMessage)
    val logger = ProcessLogger(
      { text =>
        outBuffer.append(text.trim + '\n')
      }, { text =>
        ansiCodesEscaper.escape(text)
      }
    )
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

  def isCbtModuleDir(entry: VirtualFile): Boolean =
    entry.containsDirectory("build")

  private class AnsiCodesEscaper(onMessage: String => Unit) {
    val colorEncoder = new AnsiEscapeDecoder
    val messageBuffer = new StringBuilder
    val textAcceptor = new ColoredTextAcceptor {
      override def coloredTextAvailable(text: String, attributes: Key[_]): Unit = {
        concatErrorMessages(text)
      }
    }

    def escape(text: String): Unit =
      colorEncoder.escapeText(text, ProcessOutputTypes.STDERR, textAcceptor)

    private def concatErrorMessages(text: String) =
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

}

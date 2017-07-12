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

    val onOutput = (text: String, stderr: Boolean) => {
      if (stderr) {
        taskListener.foreach {
          case (id, l) =>
            l.onStatusChange(new ExternalSystemTaskNotificationEvent(id, text))
        }
      }
    }

    val outputHandler = new CbtOutputHandler(onOutput)
    val logger = ProcessLogger(
      outputHandler.dataReceived(_, stderr = false),
      outputHandler.dataReceived(_, stderr = true))
    val exitCode = Process(Seq("cbt", "direct") ++ action, root) ! logger
    exitCode match {
      case 0 =>
        Success(outputHandler.stdOut)
      case _ =>
        Failure(new CbtProjectImporingException(outputHandler.errors.mkString("\n")))
    }
  }

  def isCbtModuleDir(entry: VirtualFile): Boolean =
    entry.containsDirectory("build")
}

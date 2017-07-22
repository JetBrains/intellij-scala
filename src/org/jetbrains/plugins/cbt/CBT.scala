package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationSource}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.plugins.cbt.project.structure.CbtProjectImporingException
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.RichVirtualFile

import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}


object CBT {
  val Icon = Icons.CBT

  def buildInfoXml(root: File, settings: CbtExecutionSettings, projectOpt: Option[Project],
                   taskListener: Option[(ExternalSystemTaskId, ExternalSystemTaskNotificationListener)]): Try[Elem] = {
    def buildParams: Seq[String] = {
      val extraModulesStr = settings.extraModules.mkString(":")
      val needCbtLibsStr = settings.isCbt.unary_!.toString
      Seq("--extraModules", extraModulesStr, "--needCbtLibs", needCbtLibsStr)
    }

    val xml = runAction("buildInfoXml" +: buildParams,settings.useDirect,root, projectOpt, taskListener)
    xml.map(XML.loadString)
  }

  def runAction(action: Seq[String], useDirect: Boolean, root: File, projectOpt: Option[Project],
                taskListener: Option[(ExternalSystemTaskId, ExternalSystemTaskNotificationListener)]): Try[String] = {
    projectOpt.foreach { project =>
      ExternalSystemNotificationManager.getInstance(project)
        .clearNotifications(NotificationSource.PROJECT_SYNC, CbtProjectSystem.Id)
    }
    val onOutput = (text: String, stderr: Boolean) => {
      if (stderr) {
        taskListener.foreach {
          case (id, l) =>
            l.onStatusChange(new ExternalSystemTaskNotificationEvent(id, text))
        }
      }
    }

    val outputHandler = new CbtOutputListener(onOutput, projectOpt, NotificationSource.PROJECT_SYNC)
    val logger = ProcessLogger(
      text => outputHandler.parseLine(text, stderr = false),
      text => outputHandler.parseLine(text + '\n', stderr = true))
    val task = Seq("cbt") ++ (if (useDirect) Seq("direct") else Seq.empty) ++ action
    val exitCode = Process(task, root) ! logger
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

package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.cbt.project.settings.CbtSystemSettings
import org.jetbrains.sbt.RichVirtualFile

import scala.sys.process.{Process, ProcessLogger}


object CBT {
  def runAction(action: Seq[String], root: File): String =
    runAction(action, root, None)

  def runAction(action: Seq[String], root: File,
                taskListener: Option[(ExternalSystemTaskId, ExternalSystemTaskNotificationListener)]): String =    {
    val colorEncoder = new AnsiEscapeDecoder
    val logger = ProcessLogger(
      { _ => }, { text =>
        taskListener.foreach {
          case (id, l) =>
            colorEncoder.escapeText(text, ProcessOutputTypes.STDERR, new ColoredTextAcceptor {
              override def coloredTextAvailable(text: String, attributes: Key[_]): Unit =
                l.onStatusChange(new ExternalSystemTaskNotificationEvent(id, text))
            })
        }
      })
    Process(Seq("cbt") ++ action, root) !! logger
  }

  def isCbtModuleDir(entry: VirtualFile): Boolean =
    entry.containsDirectory("build")

  def isCbtProject(project: Project): Boolean =
    CbtSystemSettings.getInstance(project).getLinkedProjectSettings(project.getBasePath) != null
}

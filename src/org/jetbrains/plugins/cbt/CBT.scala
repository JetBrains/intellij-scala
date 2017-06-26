package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessHandlerFactory, ProcessOutputTypes}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.cbt.project.settings.CbtSystemSettings
import org.jetbrains.sbt.RichVirtualFile


object CBT {
  def runAction(action: Seq[String], root: File): String =
    runAction(action, root, None)

  def runAction(action: Seq[String], root: File,
                taskListener: Option[(ExternalSystemTaskId, ExternalSystemTaskNotificationListener)]): String = {
    val factory = ProcessHandlerFactory.getInstance
    val commandLine = new GeneralCommandLine("cbt" +: action: _*)
      .withWorkDirectory(root)

    val buffer = new StringBuffer()
    val errorBuffer = new StringBuffer()
    val listener = new ProcessAdapter {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
        val text = event.getText
        outputType match {
          case ProcessOutputTypes.STDOUT => buffer.append(text)
          case ProcessOutputTypes.STDERR =>
          //            taskListener.foreach { case (id, l) =>
          //              if (text.contains('\n')) {
          //                val (prefix, suffix) = text.span {
          //                  _ != '\n'
          //                }
          //                errorBuffer.append(prefix)
          //                l.onStatusChange(new ExternalSystemTaskNotificationEvent(id, errorBuffer.toString))
          //                errorBuffer.setLength(0)
          //                errorBuffer.append(suffix)
          //              } else {
          //                errorBuffer.append(text)
          //              }
          //            }
        }
      }
    }

    val handler = factory.createColoredProcessHandler(commandLine)
    handler.addProcessListener(listener)
    handler.startNotify()
    handler.waitFor()
    buffer.toString
  }

  def isCbtModuleDir(entry: VirtualFile): Boolean =
    entry.containsDirectory("build")

  def isCbtProject(project: Project): Boolean =
    CbtSystemSettings.getInstance(project).getLinkedProjectSettings(project.getBasePath) != null
}

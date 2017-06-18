package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessHandlerFactory, ProcessOutputTypes}
import com.intellij.openapi.util.Key

import scala.xml._

object CBT {
  def runAction(action: Seq[String], root: File): String = {
    val factory = ProcessHandlerFactory.getInstance
    val commandLine = new GeneralCommandLine("cbt" +: action: _*)
      .withWorkDirectory(root)

    val buffer = new StringBuffer()
    val listener = new ProcessAdapter {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
        val text = event.getText
        outputType match {
          case ProcessOutputTypes.STDOUT => buffer.append(text)
          case _ =>
        }
      }
    }

    val handler = factory.createColoredProcessHandler(commandLine)
    handler.addProcessListener(listener)
    handler.startNotify()
    handler.waitFor()
    buffer.toString
  }

  def projectBuidInfo(root: File): Node =
    XML.loadString(runAction(Seq("buildInfoXml"), root))
}

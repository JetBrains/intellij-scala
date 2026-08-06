package org.jetbrains.sbt
package project.structure

import com.intellij.execution.process.{ProcessEvent, ProcessListener, ProcessOutputTypes}
import com.intellij.openapi.util.Key

class ListenerAdapter(listener: (OutputType, String) => Unit) extends ProcessListener {
  override def onTextAvailable(event: ProcessEvent, outputType: Key[?]): Unit = {
    val textType = outputType match {
      case ProcessOutputTypes.STDOUT => Some(OutputType.StdOut)
      case ProcessOutputTypes.STDERR => Some(OutputType.StdErr)
      case ProcessOutputTypes.SYSTEM => Some(OutputType.MySystem)
      case other                     => Some(OutputType.Other(other))
    }
    textType.foreach(t => listener(t, event.getText))
  }
}

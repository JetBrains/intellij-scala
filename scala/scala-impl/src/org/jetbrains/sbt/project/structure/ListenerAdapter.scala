package org.jetbrains.sbt
package project.structure

import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessOutputTypes}
import com.intellij.openapi.util.Key

/**
 * @author Pavel Fatin
 */
class ListenerAdapter(listener: (OutputType, String) => Unit) extends ProcessAdapter {
  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
    val textType = outputType match {
      case ProcessOutputTypes.STDOUT => Some(OutputType.StdOut)
      case ProcessOutputTypes.STDERR => Some(OutputType.StdErr)
      case ProcessOutputTypes.SYSTEM => Some(OutputType.MySystem)
      case other                     => Some(OutputType.Other(other))
    }
    textType.foreach(t => listener(t, event.getText))
  }
}

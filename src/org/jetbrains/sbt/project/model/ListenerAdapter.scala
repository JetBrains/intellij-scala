package org.jetbrains.sbt
package project.model

import com.intellij.execution.process.{ProcessOutputTypes, ProcessEvent, ProcessAdapter}
import com.intellij.openapi.util.Key

/**
 * @author Pavel Fatin
 */
class ListenerAdapter(listener: (OutputType, String) => Unit) extends ProcessAdapter {
  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
    val textType = outputType match {
      case ProcessOutputTypes.STDOUT => OutputType.StdOut
      case ProcessOutputTypes.STDERR => OutputType.StdErr
    }
    listener(textType, event.getText)
  }
}

package org.jetbrains.plugins.scala
package compiler

import com.intellij.execution.process._
import com.intellij.openapi.util.Key

/**
 * @author Pavel Fatin
 */
class ProcessWatcher(process: Process, commandLine: String) {
  private val processHandler = new OSProcessHandler(process, commandLine)
  private var errorLines = Vector[String]()
  private var errorInStdOut = false
  private val lock = new Object()

  processHandler.addProcessListener(MyProcessListener)

  def startNotify() {
    processHandler.startNotify()
  }

  def running: Boolean = !processHandler.isProcessTerminated

  def errors(): Seq[String] = {
    lock.synchronized {
      val result = errorLines
      errorLines = Vector()
      result
    }
  }

  def destroyProcess() {
    process.destroy()
  }

  private object MyProcessListener extends ProcessAdapter {
    override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
      val text = event.getText

      outputType match {
        case ProcessOutputTypes.STDOUT => lock.synchronized {
          if (errorInStdOut || ProcessWatcher.ExceptionPattern.matcher(text).find) {
            errorInStdOut = true
            errorLines :+= text
          }
        }

        case ProcessOutputTypes.STDERR => lock.synchronized {
          errorLines :+= text
        }

        case _ => // do nothing
      }
    }
  }
}

object ProcessWatcher {
  private val ExceptionPattern = "error|exception".r.pattern
}
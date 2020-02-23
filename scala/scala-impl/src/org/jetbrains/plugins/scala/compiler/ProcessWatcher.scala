package org.jetbrains.plugins.scala
package compiler

import java.util.concurrent.TimeUnit

import com.intellij.execution.process._
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader

/**
 * @author Pavel Fatin
 */
class ProcessWatcher(process: Process, commandLine: String) {
  private val processHandler = new OSProcessHandler(process, commandLine) {
    override def readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.BLOCKING
  }
  private var errorLines = Vector[String]()
  private var errorInStdOut = false
  private val lock = new Object()

  processHandler.addProcessListener(MyProcessListener)

  def startNotify(): Unit = {
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

  //true if process exited before timeout
  def destroyAndWait(ms: Long): Boolean = {
    process.destroy()
    process.waitFor(ms, TimeUnit.MILLISECONDS)
  }

  private object MyProcessListener extends ProcessAdapter {
    override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
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
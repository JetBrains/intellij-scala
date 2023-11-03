package org.jetbrains.plugins.scala.compiler

import com.intellij.execution.process._
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import org.jetbrains.jps.incremental.scala.utils.CompileServerSharedMessages
import org.jetbrains.plugins.scala.compiler.ProcessWatcher.Log
import org.jetbrains.plugins.scala.extensions.invokeLater

private final class ProcessWatcher(project: Project, process: Process, commandLine: String) {
  private val processHandler = new OSProcessHandler(process, commandLine) {
    override def readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.BLOCKING
  }

  @volatile
  private var errorInStdOut = false

  addProcessListener(MyProcessListener)

  def addProcessListener(listener: ProcessAdapter): Unit =
    processHandler.addProcessListener(listener)

  def startNotify(): Unit = {
    processHandler.startNotify()
  }

  def running: Boolean = !processHandler.isProcessTerminated

  def pid: Long = process.pid()

  def destroyAndWait(): Boolean = {
    processHandler.destroyProcess()
    processHandler.waitFor()
  }

  //true if process exited before timeout
  def destroyAndWaitFor(ms: Long): Boolean = {
    processHandler.destroyProcess()
    processHandler.waitFor(ms)
  }

  private var _terminatedByIdleTimeout = false
  def isTerminatedByIdleTimeout: Boolean = _terminatedByIdleTimeout

  private object MyProcessListener extends ProcessAdapter {
    override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
      val text = event.getText

      //print(s"[$outputType] $text")
      outputType match {
        case ProcessOutputTypes.STDOUT =>
          if (errorInStdOut || ProcessWatcher.ExceptionPattern.matcher(text).find) {
            errorInStdOut = true
            processErrorText(text, outputType)
          }

          if (text.startsWith(CompileServerSharedMessages.CompileServerShutdownPrefix)) {
            Log.info(s"[$outputType] ${text.stripTrailing()}")
            if (text.contains(CompileServerSharedMessages.ProcessWasIdleFor)) {
              _terminatedByIdleTimeout = true
              invokeLater {
                if (!project.isDisposed) {
                  CompileServerManager(project).showStoppedByIdleTimoutNotification()
                }
              }
            }
          }

        case ProcessOutputTypes.STDERR =>
          processErrorText(text, outputType)

        case _ => // do nothing
      }
    }

    private def processErrorText(text: String, outputType: Key[_]): Unit = {
      Log.warn(s"[$outputType] ${text.trim}")
      val filtered = text.linesIterator.mkString(System.lineSeparator())
      if (filtered.nonEmpty) {
        project.getMessageBus.syncPublisher(CompileServerManager.ErrorTopic).onError(filtered)
      }
    }

    override def processTerminated(event: ProcessEvent): Unit = {
      Log.info(s"compile server process terminated with exit code: ${event.getExitCode} (pid: ${process.pid()}) ")
    }
  }
}

object ProcessWatcher {
  private val Log = Logger.getInstance(classOf[ProcessWatcher])
  private val ExceptionPattern = "[eE]rror|[eE]xception".r.pattern
}

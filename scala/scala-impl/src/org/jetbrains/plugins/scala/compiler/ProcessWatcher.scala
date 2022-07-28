package org.jetbrains.plugins.scala.compiler

import com.intellij.execution.process._
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import org.jetbrains.jps.incremental.scala.utils.CompileServerSharedMessages
import org.jetbrains.plugins.scala.compiler.ProcessWatcher.{Log, ignoreErrorTextLine}
import org.jetbrains.plugins.scala.extensions.invokeLater

private class ProcessWatcher(project: Project, process: Process, commandLine: String) {
  private val processHandler = new OSProcessHandler(process, commandLine) {
    override def readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.BLOCKING
  }
  private var errorTextBuilder: StringBuilder = new StringBuilder
  private var errorInStdOut = false
  private val lock = new Object()

  addProcessListener(MyProcessListener)

  def addProcessListener(listener: ProcessAdapter): Unit =
    processHandler.addProcessListener(listener)

  def startNotify(): Unit = {
    processHandler.startNotify()
  }

  def running: Boolean = !processHandler.isProcessTerminated

  def pid: Long = process.pid()

  def errorsText(): String = {
    lock.synchronized {
      if (errorTextBuilder.nonEmpty) {
        val result = errorTextBuilder
        errorTextBuilder = new StringBuilder
        result.mkString.linesIterator.filterNot(ignoreErrorTextLine).mkString("\n")
      }
      else ""
    }
  }

  //true if process exited before timeout
  def destroyAndWait(ms: Long): Boolean = {
    processHandler.destroyProcess()
    processHandler.waitFor(ms)
  }

  private var _terminatedByIdleTimeout = false
  def isTerminatedByIdleTimeout = _terminatedByIdleTimeout

  private object MyProcessListener extends ProcessAdapter {
    override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
      val text = event.getText

      //print(s"[$outputType] $text")
      outputType match {
        case ProcessOutputTypes.STDOUT => lock.synchronized {
          if (errorInStdOut || ProcessWatcher.ExceptionPattern.matcher(text).find) {
            errorInStdOut = true
            processErrorText(text, outputType)
          }

          if (text.startsWith(CompileServerSharedMessages.CompileServerShutdownPrefix)) {
            Log.info(s"[$outputType] ${text.stripTrailing()}")
            if (text.contains(CompileServerSharedMessages.ProcessWasIdleFor)) {
              _terminatedByIdleTimeout = true
              invokeLater {
                if (project.isDisposed) {
                  CompileServerManager(project).showStoppedByIdleTimoutNotification()
                }
              }
            }
          }
        }

        case ProcessOutputTypes.STDERR => lock.synchronized {
          processErrorText(text, outputType)
        }

        case _ => // do nothing
      }
    }

    private def processErrorText(text: String, outputType: Key[_]): Unit = {
      Log.warn(s"[$outputType] ${text.trim}")
      errorTextBuilder.append(text)
    }

    override def processTerminated(event: ProcessEvent): Unit = {
      Log.info(s"compile server process terminated with exit code: ${event.getExitCode} (pid: ${process.pid()}) ")
    }
  }
}

object ProcessWatcher {
  private val Log = Logger.getInstance(classOf[ProcessWatcher])
  private val ExceptionPattern = "[eE]rror|[eE]xception".r.pattern

  private def ignoreErrorTextLine(text: String): Boolean =
    isJDK17SecurityManagerWarningLine(text)

  //Temp (hopefully) workaround for SCL-19556, SCL-19470, SCL-18150
  //See implementation of java.lang.System.setSecurityManager in JDK 17 and https://openjdk.java.net/jeps/411
  //UPDATE: in JDK 18 they now throw an exception, not just print a warning, see SCL-20064,
  // this is workaround in CompileServerLauncher by passing -Djava.security.manager=allow
  private def isJDK17SecurityManagerWarningLine(text: String) = {
    text.linesIterator.exists { line =>
      line.startsWith("WARNING: A terminally deprecated method in java.lang.System has been called") ||
        line.startsWith("WARNING: System::setSecurityManager has been called by com.facebook.nailgun.NGServer") ||
        line.startsWith("WARNING: Please consider reporting this to the maintainers of com.facebook.nailgun.NGServer") ||
        line.startsWith("WARNING: System::setSecurityManager will be removed in a future release")
    }
  }
}
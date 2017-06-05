package org.jetbrains.plugins.scala
package compiler

import java.io.{BufferedReader, File, InputStreamReader, Reader}
import java.util.concurrent.Future

import com.intellij.execution.TaskExecutor
import com.intellij.execution.process._
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Consumer
import com.intellij.util.io.BaseDataReader

import _root_.scala.collection.JavaConverters._

/**
 * User: Dmitry Naydanov
 * Date: 2/11/14
 */
class NonServerRunner(project: Project, errorHandler: Option[ErrorHandler] = None) {
  private val SERVER_CLASS_NAME = "org.jetbrains.jps.incremental.scala.remote.Main"

  private def classPath(jdk: JDK) = (jdk.tools +: CompileServerLauncher.compilerJars).map(
    file => FileUtil toCanonicalPath file.getPath).mkString(File.pathSeparator)

  private val jvmParameters = CompileServerLauncher.jvmParameters
  
  def buildProcess(args: Seq[String], listener: String => Unit): CompilationProcess = {
    CompileServerLauncher.compilerJars.foreach(p => assert(p.exists(), p.getPath))

    CompileServerLauncher.compileServerJdk(project) match {
      case None =>
        null
      case Some(jdk) =>
        val commands = ((FileUtil toCanonicalPath jdk.executable.getPath) +: "-cp" +: classPath(jdk) +: jvmParameters :+ 
          SERVER_CLASS_NAME).++(args)

        val builder = new ProcessBuilder(commands.asJava)
        
        new CompilationProcess {
          var myProcess: Option[Process] = None
          var myCallbacks: Seq[() => Unit] = Seq.empty

          override def addTerminationCallback(callback: => Unit) {
            myCallbacks = myCallbacks :+ (() => callback)
          }

          override def run() {
            val p = builder.start()
            myProcess = Some(p)

            val reader = new BufferedReader(new InputStreamReader(p.getInputStream))
            new MyBase64StreamReader(reader, listener)

            val processWaitFor = new ProcessWaitFor(p, new TaskExecutor {
              override def executeTask(task: Runnable): Future[_] = BaseOSProcessHandler.ExecutorServiceHolder.submit(task)
            })

            processWaitFor.setTerminationCallback(new Consumer[Integer] {
              override def consume(t: Integer) {
                myCallbacks.foreach(c => c())
              }
            })
          }

          override def stop() {
            myProcess foreach (_.destroy())
            myProcess = None
          }
        }
    }
  }
  
  private def error(message: String) {
    errorHandler.foreach(_.error(message))
  }
  
  private class MyBase64StreamReader(private val reader: Reader, listener: String => Unit) extends BaseDataReader(null) {
    start()
    
    private val charBuffer = new Array[Char](8192)
    private val text = new StringBuilder
    
    def executeOnPooledThread(runnable: Runnable): Future[_] =
      BaseOSProcessHandler.ExecutorServiceHolder.submit(runnable)

    def onTextAvailable(text: String) {
      try {
        listener(text)
      }
      catch {
        case _: Exception =>
      }
    }

    override def close() {
      reader.close()
    }

    override def readAvailable(): Boolean = {
      var read = false
      
      while (reader.ready()) {
        val n = reader.read(charBuffer)
        
        if (n > 0) {
          read = true
          
          for (i <- 0 until n) {
            charBuffer(i) match {
              case '=' if i == 0 && text.isEmpty =>
              case '=' if i == n - 1 || charBuffer.charAt(i + 1) != '=' =>
                if ( (text.length +1) % 4 == 0 ) text.append('=') else if ( (text.length + 2) % 4 == 0 ) text.append("==")
                onTextAvailable(text.toString())
                text.clear()
              case '\n' if text.nonEmpty && text.startsWith("Listening") => 
                text.clear()
              case c => text.append(c) 
            }
          }
        }
      }
      
      read
    }
  }
}

trait ErrorHandler {
  def error(message: String): Unit
}

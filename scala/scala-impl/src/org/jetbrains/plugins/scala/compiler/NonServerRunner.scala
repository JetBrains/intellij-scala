package org.jetbrains.plugins.scala
package compiler

import java.io.{BufferedReader, File, InputStreamReader, Reader}
import java.util.Base64
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.execution.process._
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.BaseDataReader
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.{ClientEventProcessor, Event, TraceEvent}

import _root_.scala.collection.JavaConverters._

/**
 * @see [[RemoteServerRunner]]
 */
class NonServerRunner(project: Project) {

  private val SERVER_CLASS_NAME = "org.jetbrains.plugins.scala.nailgun.MainLightRunner"

  private def classPathArg(jars: Seq[File]): String = {
    val jarPaths = jars.map(_.getPath).map(FileUtil.toCanonicalPath)
    jarPaths.mkString(File.pathSeparator)
  }

  private val jvmParameters = CompileServerLauncher.jvmParameters

  def buildProcess(args: Seq[String], client: Client): CompilationProcess = {
    CompileServerLauncher.compileServerJars.foreach(p => assert(p.exists(), p.getPath))

    val jdk = CompileServerLauncher.compileServerJdk(project)
    jdk match {
      case None =>
        null
      case Some(jdk) =>
        val commands: Seq[String] = {
          val jdkPath = FileUtil.toCanonicalPath(jdk.executable.getPath)
          val runnerClassPath = classPathArg(jdk.tools.toSeq :+ PluginJars.scalaNailgunRunnerJar)
          val mainClassPath = classPathArg(jdk.tools.toSeq ++ CompileServerLauncher.compileServerJars)
          (jdkPath +: "-cp" +: runnerClassPath +: jvmParameters) ++
            (SERVER_CLASS_NAME +: "-classpath" +: mainClassPath +: args)
        }

        val builder = new ProcessBuilder(commands.asJava)
        //builder.redirectErrorStream(true)

        new CompilationProcess {
          var myProcess: Option[Process] = None
          var myCallbacks: Seq[Option[Throwable] => Unit] = Seq.empty
          val myCallbacksHandled: AtomicBoolean = new AtomicBoolean(false)

          override def addTerminationCallback(callback: Option[Throwable] => Unit): Unit = this.myCallbacks :+= callback

          override def run(): Unit = try {
            val p = builder.start()
            myProcess = Some(p)

            val eventClient = new ClientEventProcessor(client)
            val listener: String => Unit = (text: String) => {
              try {
                val bytes = Base64.getDecoder.decode(text.getBytes("UTF-8"))
                val event = Event.fromBytes(bytes)
                eventClient.process(event)
              } catch {
                case _: IllegalArgumentException =>
                  // probably some unexpected text from stderr
                  eventClient.process(TraceEvent("", text, Array()))
              }
            }
            val bufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream))
            val reader = new MyBase64StreamReader(bufferedReader, listener) //starts threads under the hood

            val processName = "Non-server worksheet runner"
            val processWaitFor =
              new ProcessWaitFor(p, (task: Runnable) => AppExecutorUtil.getAppExecutorService.submit(task), processName)

            processWaitFor.setTerminationCallback { returnCode =>
              if (myCallbacksHandled.compareAndSet(false, true)) {
                val ex = if (returnCode == 0) None else Some(new RuntimeException(s"process terminated with return code: $returnCode"))
                myCallbacks.foreach(_.apply(ex))
              }
              reader.stop() // will close streams under the hood in event loop
            }
          } catch {
            case ex: Throwable =>
              if(myCallbacksHandled.compareAndSet(false, true)) {
                myCallbacks.foreach(_.apply(Some(ex)))
              }
              throw ex
          }

          override def stop(): Unit = {
            myProcess.foreach(_.destroy())
            myProcess = None
          }
        }
    }
  }


  private class MyBase64StreamReader(private val reader: Reader, listener: String => Unit) extends BaseDataReader(null) {
    start(project.getName)
    
    private val charBuffer = new Array[Char](8192)
    private val text = new StringBuilder
    
    override def executeOnPooledThread(runnable: Runnable): Future[_] =
      AppExecutorUtil.getAppExecutorService.submit(runnable)

    def onTextAvailable(text: String): Unit = {
      try {
        listener(text)
      }
      catch {
        case _: Exception =>
      }
    }

    override def close(): Unit = {
      if (text.nonEmpty) {
        onTextAvailable(text.toString())
        text.clear()
      }
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
                if ((text.length + 1) % 4 == 0) {
                  text.append('=')
                } else if ((text.length + 2) % 4 == 0) {
                  text.append("==")
                }
                onTextAvailable(text.toString())
                text.clear()
              case '\n' if text.nonEmpty && text.startsWith("Listening") => 
                text.clear()
              case c =>
                text.append(c)
            }
          }
        }
      }
      
      read
    }
  }
}
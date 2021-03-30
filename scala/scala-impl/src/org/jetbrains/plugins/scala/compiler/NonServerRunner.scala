package org.jetbrains.plugins.scala
package compiler

import java.io.{BufferedReader, File, InputStreamReader, Reader}
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.compiler.server.BuildManager
import com.intellij.execution.process._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.BaseDataReader
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.{ClientEventProcessor, Event, TraceEvent}
import org.jetbrains.plugins.scala.compiler.NonServerRunner.Log
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils
import org.jetbrains.plugins.scala.util.ScalaPluginJars

import _root_.scala.jdk.CollectionConverters._

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
      case Left(error) => // TODO: propagate error
        null
      case Right(jdk) =>
        // in non-server mode token is ignored, but is required in order args are parsed correctly
        val argsEncoded = ("IGNORED_TOKEN" +: args).map { arg =>
          // When we call main method starting new process we have to use some stub for empty argument, otherwise the argument will be skipped
          // (when sending arguments via socket, Nailgun automatically recognises empty argument and processes them correctly)
          val argFixed = if (arg.isEmpty) SerializationUtils.EmptyArgumentStub else arg
          Base64.getEncoder.encodeToString(argFixed.getBytes(StandardCharsets.UTF_8))
        }
        val commands: Seq[String] = {
          val jdkPath = FileUtil.toCanonicalPath(jdk.executable.getPath)
          val runnerClassPath = classPathArg(jdk.tools.toSeq :+ ScalaPluginJars.scalaNailgunRunnerJar)
          val mainClassPath = classPathArg(jdk.tools.toSeq ++ CompileServerLauncher.compileServerJars)
          val buildSystemDir = BuildManager.getInstance.getBuildSystemDirectory(project).toFile.getCanonicalPath
          (jdkPath +: "-cp" +: runnerClassPath +: jvmParameters) ++
            (SERVER_CLASS_NAME +: mainClassPath +: buildSystemDir +: argsEncoded)
        }

        val builder = new ProcessBuilder(commands.asJava)
        //builder.redirectErrorStream(true)

        new CompilationProcess {
          var myProcess: Option[Process] = None
          var myCallbacks: Seq[Option[Throwable] => Unit] = Seq.empty
          val myCallbacksHandled: AtomicBoolean = new AtomicBoolean(false)

          override def addTerminationCallback(callback: Option[Throwable] => Unit): Unit = this.myCallbacks :+= callback


          private def finish(result: Option[Throwable]): Unit = {
            myCallbacks.foreach(_.apply(result))
          }

          override def run(): Unit = try {
            val p = builder.start()

            if (ApplicationManager.getApplication.isUnitTestMode) {
              Log.debug(s"NonServerRunner process command line: ${builder.command().asScala.mkString(" ")}")
            }

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

            val bufferedErrorsReader = new BufferedReader(new InputStreamReader(p.getErrorStream))
            val errorsReader = new CollectingStreamReader(bufferedErrorsReader, s"error stream  : ${project.getName}")

            val processName = "Non-server worksheet runner"
            val processWaitFor =
              new ProcessWaitFor(p, (task: Runnable) => AppExecutorUtil.getAppExecutorService.submit(task), processName)

            processWaitFor.setTerminationCallback { returnCode =>
              if (myCallbacksHandled.compareAndSet(false, true)) {
                val ex = if (returnCode == 0) None else {
                  Log.error(s"NonServerRunner process output:\n${errorsReader.getText}")
                  Some(new RuntimeException(s"process terminated with return code: $returnCode"))
                }
                finish(ex)
              }

              errorsReader.stop()
              reader.stop() // will close streams under the hood in event loop
            }
          } catch {
            case ex: Throwable =>
              if(myCallbacksHandled.compareAndSet(false, true)) {
                finish(Some(ex))
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
  private class CollectingStreamReader(
    reader: Reader,
    presentableName: String
  ) extends BaseDataReader(null) {
    start(presentableName)

    private val charBuffer = new Array[Char](8192)
    private val text = new java.lang.StringBuilder
    def getText: String = text.toString

    override def executeOnPooledThread(runnable: Runnable): Future[_] =
      AppExecutorUtil.getAppExecutorService.submit(runnable)

    override def close(): Unit = {
      reader.close()
    }

    override def readAvailable(): Boolean = {
      var read = false

      while (reader.ready()) {
        val n = reader.read(charBuffer)
        if (n > 0) {
          read = true
          text.append(charBuffer, 0, n)
        }
      }

      read
    }
  }
}

object NonServerRunner {
  private val Log = Logger.getInstance(getClass)
}
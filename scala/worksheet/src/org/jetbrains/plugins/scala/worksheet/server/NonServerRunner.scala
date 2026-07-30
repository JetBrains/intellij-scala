package org.jetbrains.plugins.scala.worksheet.server

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
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils
import org.jetbrains.plugins.scala.compiler.{CompilationProcess, CompileServerLauncher}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.ScalaPluginJars
import org.jetbrains.plugins.scala.worksheet.server.NonServerRunner.Log

import java.io.{BufferedReader, InputStreamReader, Reader}
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters._

/**
 * @see [[RemoteServerRunner]]
 */
class NonServerRunner(project: Project) {

  private val SERVER_CLASS_NAME = "org.jetbrains.plugins.scala.nailgun.MainLightRunner"

  private def classPathArg(jars: Seq[Path]): String = {
    val jarPaths = jars.map(_.toString).map(FileUtil.toCanonicalPath)
    jarPaths.mkString(java.io.File.pathSeparator)
  }

  def buildProcess(args: Seq[String], client: Client): CompilationProcess = {
    CompileServerLauncher.compileServerJars.foreach(p => assert(p.exists, p))

    val jdk = CompileServerLauncher.compileServerJdk(project)
    jdk match {
      case Left(error) => // TODO: propagate error
        null
      case Right(jdk) =>
        // in non-server mode token is ignored, but is required in order args are parsed correctly
        val argsEncoded = ("IGNORED_TOKEN" +: args).map { arg =>
          // When we call main method starting new process we have to use some stub for empty argument, otherwise the argument will be skipped
          // (when sending arguments via socket, Nailgun automatically recognises empty argument and processes them correctly)
          if (arg.isEmpty) SerializationUtils.EmptyArgumentStub else arg
        }
        val commands: Seq[String] = {
          val jdkPath = jdk.executable.toCanonicalPath.toString
          val jdkToolsPath = jdk.tools.toSeq
          val runnerClassPath = classPathArg(jdkToolsPath :+ ScalaPluginJars.scalaNailgunRunnerJar)
          val mainClassPath = classPathArg(jdkToolsPath ++ CompileServerLauncher.compileServerJars)
          val scalaCompileServerSystemDir = CompileServerLauncher.scalaCompileServerSystemDir(project)
          val jpsBuildSystemDir = BuildManager.getInstance().getBuildSystemDirectory(project)
          val jvmParameters = CompileServerLauncher.jvmParameters
          val jnaParams = CompileServerLauncher.jnaVMOptions
          val java9rtJarParams = CompileServerLauncher.prepareJava9rtJar(project, jdk)
          (jdkPath +: "-cp" +: runnerClassPath +: jvmParameters) ++ jnaParams ++ java9rtJarParams ++
            (SERVER_CLASS_NAME +: mainClassPath +: scalaCompileServerSystemDir.toString +: jpsBuildSystemDir.toString +: argsEncoded)
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
                val bytes = Base64.getDecoder.decode(text.getBytes(StandardCharsets.UTF_8))
                val event = Event.fromBytes(bytes)
                eventClient.process(event)
              } catch {
                case _: IllegalArgumentException =>
                  // probably some unexpected text from stderr
                  eventClient.process(TraceEvent("", text, Array()))
              }
            }
            val bufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream))
            val reader = new MyStreamReader(bufferedReader, listener) //starts threads under the hood

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


  // Pass the sleeping policy explicitly: since 263.2278 a `null` policy defaults to BLOCKING,
  // in which the reader closes the process streams right after the first empty non-blocking read.
  private class MyStreamReader(private val reader: Reader, listener: String => Unit)
    extends BaseDataReader(BaseDataReader.SleepingPolicy.NON_BLOCKING) {

    start(project.getName)

    private val charBuffer = new Array[Char](8192)
    private val text = new StringBuilder

    override def executeOnPooledThread(runnable: Runnable): Future[?] =
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

    override def readAvailableNonBlocking(): Boolean = {
      var read = false

      while (reader.ready()) {
        val n = reader.read(charBuffer)

        if (n > 0) {
          read = true

          for (i <- 0 until n) {
            charBuffer(i) match {
              case '=' if i == 0 && text.isEmpty =>
              case '=' if i == n - 1 || charBuffer(i + 1) != '=' =>
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
  ) extends BaseDataReader(BaseDataReader.SleepingPolicy.NON_BLOCKING) {
    start(presentableName)

    private val charBuffer = new Array[Char](8192)
    private val text = new java.lang.StringBuilder
    def getText: String = text.toString

    override def executeOnPooledThread(runnable: Runnable): Future[?] =
      AppExecutorUtil.getAppExecutorService.submit(runnable)

    override def close(): Unit = {
      reader.close()
    }

    override def readAvailableNonBlocking(): Boolean = {
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
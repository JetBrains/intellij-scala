package org.jetbrains.plugins.scala
package debugger.evaluation

import java.io.File
import java.util.concurrent.TimeUnit

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.{DebuggerManagerAdapter, DebuggerSession}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, RemoteServerConnectorBase, RemoteServerRunner, ScalaCompileServerSettings}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Try}

/**
 * Nikolay.Tropin
 * 2014-10-07
 */
class ScalaEvaluatorCompileHelper(project: Project) extends AbstractProjectComponent(project) with EvaluatorCompileHelper {

  private val tempFiles = mutable.Set[File]()

  override def projectOpened(): Unit = {
    DebuggerManagerEx.getInstanceEx(project).addDebuggerManagerListener(
      new DebuggerManagerAdapter {
        override def sessionAttached(session: DebuggerSession): Unit = {
          if (EvaluatorCompileHelper.needCompileServer && project.hasScala) {
            CompileServerLauncher.ensureServerRunning(project)
          }
        }

        override def sessionDetached(session: DebuggerSession) = {
          for (f <- tempFiles) {
            FileUtil.delete(f)
          }

          if (!ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED && EvaluatorCompileHelper.needCompileServer) {
            CompileServerLauncher.ensureNotRunning(project)
          }
        }
      }
    )
  }

  def tempDir() = {
    val dir = FileUtil.createTempDirectory("classfilesForDebugger", null, true)
    tempFiles += dir
    dir
  }

  def tempFile() = {
    val file = FileUtil.createTempFile("FileToCompile", ".scala", true)
    tempFiles += file
    file
  }

  def compile(fileText: String, module: Module): Array[(File, String)] = {
    val outputDir = tempDir()
    val file = writeToTempFile(fileText)
    val connector = new ServerConnector(module, file, outputDir)
    val futureFiles = connector.compile()
    try Await.result(futureFiles, Duration(5, TimeUnit.SECONDS))
    catch {
      case _: TimeoutException => throw EvaluationException("Too long compilation")
      case e: Exception => throw EvaluationException("Could not compile:\n" + e.getMessage)
    }
  }
  
  def writeToTempFile(text: String): File = {
    val file = tempFile()
    FileUtil.writeToFile(file, text)
    file
  }
}

object ScalaEvaluatorCompileHelper {
  def instance(project: Project) = project.getComponent(classOf[ScalaEvaluatorCompileHelper])
}


private class ServerConnector(module: Module, file: File, outputDir: File) extends RemoteServerConnectorBase(module, file, outputDir) {

  val errors = ListBuffer[String]()

  val client = new Client {
    override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]): Unit = {
      if (kind == Kind.ERROR) errors += text
    }
    override def deleted(module: File): Unit = {}
    override def progress(text: String, done: Option[Float]): Unit = {}
    override def isCanceled: Boolean = false
    override def debug(text: String): Unit = {}
    override def processed(source: File): Unit = {}
    override def trace(exception: Throwable): Unit = {}
    override def generated(source: File, module: File, name: String): Unit = {}
  }

  @tailrec
  private def classfiles(dir: File, namePrefix: String = ""): Array[(File, String)] = dir.listFiles() match {
    case Array(d) if d.isDirectory => classfiles(d, s"$namePrefix${d.getName}.")
    case files => files.map(f => (f, s"$namePrefix${f.getName}".stripSuffix(".class")))
  }

  def compile(): Future[Array[(File, String)]] = {
    val project = module.getProject

    val compilationProcess = new RemoteServerRunner(project).buildProcess(arguments, client)
    val promise = Promise[Array[(File, String)]]()
    compilationProcess.addTerminationCallback {
      if (errors.nonEmpty) promise.complete(Failure(EvaluationException(errors.mkString("\n"))))
      else promise.complete(Try(classfiles(outputDir)))
    }
    compilationProcess.run()
    promise.future
  }
}

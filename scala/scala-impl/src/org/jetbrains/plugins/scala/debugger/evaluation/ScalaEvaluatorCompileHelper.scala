package org.jetbrains.plugins.scala
package debugger.evaluation

import java.io.File

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.{DebuggerManagerListener, DebuggerSession}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
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

/**
 * Nikolay.Tropin
 * 2014-10-07
 */
class ScalaEvaluatorCompileHelper(project: Project) extends ProjectComponent with EvaluatorCompileHelper {

  private val tempFiles = mutable.Set[File]()

  private val listener = new DebuggerManagerListener {
    override def sessionAttached(session: DebuggerSession): Unit = {
      if (EvaluatorCompileHelper.needCompileServer && project.hasScala) {
        CompileServerLauncher.ensureServerRunning(project)
      }
    }

    override def sessionDetached(session: DebuggerSession): Unit = {
      clearTempFiles()

      if (!ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED && EvaluatorCompileHelper.needCompileServer) {
        CompileServerLauncher.ensureNotRunning(project)
      }
    }
  }

  override def projectOpened(): Unit = {
    if (!ApplicationManager.getApplication.isUnitTestMode) {
      DebuggerManagerEx.getInstanceEx(project).addDebuggerManagerListener(listener)
    }
  }

  override def projectClosed(): Unit = {
    DebuggerManagerEx.getInstanceEx(project).removeDebuggerManagerListener(listener)
  }

  private def clearTempFiles(): Unit = {
    tempFiles.foreach(FileUtil.delete)
    tempFiles.clear()
  }

  def tempDir(): File = {
    val dir = FileUtil.createTempDirectory("classfilesForDebugger", null, true)
    tempFiles += dir
    dir
  }

  def tempFile(): File = {
    val file = FileUtil.createTempFile("FileToCompile", ".scala", true)
    tempFiles += file
    file
  }

  def compile(fileText: String, module: Module): Array[(File, String)] = {
    compile(fileText, module, tempDir())
  }

  def compile(files: Seq[File], module: Module, outputDir: File): Array[(File, String)] = {
    assert(CompileServerLauncher.ensureServerRunning(project))
    val connector = new ServerConnector(module, files, outputDir)
    try {
      connector.compile() match {
        case Left(output) => output
        case Right(errors) => throw EvaluationException(errors.mkString("\n"))
      }
    }
    catch {
      case e: Exception => throw EvaluationException("Could not compile:\n" + e.getMessage)
    }
  }

  def compile(fileText: String, module: Module, outputDir: File): Array[(File, String)] = {
    compile(Seq(writeToTempFile(fileText)), module, outputDir)
  }

  def writeToTempFile(text: String): File = {
    val file = tempFile()
    FileUtil.writeToFile(file, text)
    file
  }
}

object ScalaEvaluatorCompileHelper {
  def instance(project: Project): ScalaEvaluatorCompileHelper = project.getComponent(classOf[ScalaEvaluatorCompileHelper])
}


private class ServerConnector(module: Module, filesToCompile: Seq[File], outputDir: File)
  extends RemoteServerConnectorBase(module, filesToCompile, outputDir) {

  private val errors: ListBuffer[String] = ListBuffer[String]()

  private val client: Client = new Client {

    override def message(msg: Client.ClientMsg): Unit =
      if (msg.kind == Kind.ERROR) errors += msg.text

    override def deleted(module: File): Unit = {}
    override def progress(text: String, done: Option[Float]): Unit = {}
    override def isCanceled: Boolean = false
    override def debug(text: String): Unit = {}
    override def trace(exception: Throwable): Unit = {}
    override def generated(source: File, module: File, name: String): Unit = {}
  }

  @tailrec
  private def classfiles(dir: File, namePrefix: String = ""): Array[(File, String)] = dir.listFiles() match {
    case Array(d) if d.isDirectory => classfiles(d, s"$namePrefix${d.getName}.")
    case files => files.map(f => (f, s"$namePrefix${f.getName}".stripSuffix(".class")))
  }

  def compile(): Either[Array[(File, String)], Seq[String]] = {
    val project = module.getProject

    val compilationProcess = new RemoteServerRunner(project).buildProcess(arguments, client)
    var result: Either[Array[(File, String)], Seq[String]] = Right(Seq("Compilation failed"))
    compilationProcess.addTerminationCallback { exception => // TODO: do not ignore possible exception
      result = if (errors.nonEmpty) Right(errors) else Left(classfiles(outputDir))
    }
    compilationProcess.run()
    result
  }
}

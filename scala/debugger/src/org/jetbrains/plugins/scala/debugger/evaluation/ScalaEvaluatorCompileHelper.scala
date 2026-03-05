package org.jetbrains.plugins.scala
package debugger
package evaluation

import com.intellij.debugger.impl.{DebuggerManagerListener, DebuggerSession}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.{FileUtil, NioFiles}
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.concurrent.duration.Duration

//noinspection ApiStatus
class ScalaEvaluatorCompileHelper(project: Project) extends EvaluatorCompileHelper {

  private val tempFiles = mutable.Set[Path]()

  private def clearTempFiles(): Unit = {
    tempFiles.foreach(NioFiles.deleteRecursively)
    tempFiles.clear()
  }

  private def tempDir(): Path = {
    val dir = FileUtil.createTempDirectory("classfilesForDebugger", null, true).toPath
    tempFiles += dir
    dir
  }

  private def tempFile(): Path = {
    val file = FileUtil.createTempFile("FileToCompile", ".scala", true).toPath
    tempFiles += file
    file
  }

  override def compile(fileText: String, module: Module): Array[(Path, String)] = {
    compile(fileText, module, tempDir())
  }

  def compile(files: Seq[Path], module: Module, outputDir: Path): Array[(Path, String)] = {
    if (EvaluatorCompileHelper.needCompileServer) {
      CompileServerLauncher.ensureServerRunning(project)
    }
    val connector = new ServerConnector(module, files, outputDir)
    try {
      connector.compile() match {
        case Right(output) => output
        case Left(errors) => throw EvaluationException(NlsString.force(errors.mkString("\n")))
      }
    }
    catch {
      case e: Exception => throw EvaluationException(DebuggerBundle.message("could.not.compile", e.getMessage))
    }
  }

  def compile(fileText: String, module: Module, outputDir: Path): Array[(Path, String)] = {
    compile(Seq(writeToTempFile(fileText)), module, outputDir)
  }

  private def writeToTempFile(text: String): Path = {
    val file = tempFile()
    Files.writeString(file, text)
    file
  }
}

object ScalaEvaluatorCompileHelper {
  def instance(project: Project): ScalaEvaluatorCompileHelper =
    project.getService(classOf[ScalaEvaluatorCompileHelper])

  private class Listener(project: Project) extends DebuggerManagerListener {

    override def sessionDetached(session: DebuggerSession): Unit = {
      instance(project).clearTempFiles()

      if (!ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED && EvaluatorCompileHelper.needCompileServer) {
        CompileServerLauncher.stopServerAndWaitFor(Duration.Zero)
      }
    }
  }
}

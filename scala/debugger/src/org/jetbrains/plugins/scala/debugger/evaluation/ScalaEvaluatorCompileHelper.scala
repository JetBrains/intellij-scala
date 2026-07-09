package org.jetbrains.plugins.scala
package debugger
package evaluation

import com.intellij.debugger.impl.{DebuggerManagerListener, DebuggerSession}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.provider.utils.EelProjectUtils
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.concurrent.duration.Duration

//noinspection ApiStatus,UnstableApiUsage
class ScalaEvaluatorCompileHelper(project: Project) extends EvaluatorCompileHelper {

  private val tempFiles: mutable.Set[Path] = mutable.Set.empty

  private def clearTempFiles(): Unit = {
    tempFiles.foreach(NioFiles.deleteRecursively)
    tempFiles.clear()
  }

  private def tempDir(): Path = {
    val dir = EelProjectUtils.createTemporaryDirectory(project, "classfilesForDebugger", "", true)
    tempFiles += dir
    dir
  }

  private def tempFile(): Path = {
    val file = EelProjectUtils.createTemporaryFile(project, "FileToCompile", ".scala", true)
    tempFiles += file
    file
  }

  override def compile(fileText: String, module: Module): Array[(Path, String)] = {
    if (EvaluatorCompileHelper.needCompileServer) {
      CompileServerLauncher.ensureServerRunning(project)
    }
    val outputDir = tempDir()
    val sourceFile = tempFile()
    Files.writeString(sourceFile, fileText)
    val connector = new ServerConnector(module, Seq(sourceFile), outputDir)
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

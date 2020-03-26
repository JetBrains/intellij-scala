package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.io.PathKt
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.scala.remote.CompileServerCommand
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerLock, RemoteServerRunner}
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Try

trait JpsCompiler {
  def compile(): Unit
}

object JpsCompiler {

  def get(project: Project): JpsCompiler =
    ServiceManager.getService(project, classOf[JpsCompiler])
}

private class JpsCompilerImpl(project: Project)
  extends JpsCompiler {

  // SCL-17295
  @Cached(ProjectRootManager.getInstance(project), null)
  private def saveProjectOnce(): Unit = project.save()

  override def compile(): Unit = {
    saveProjectOnce()
    CompileServerLauncher.ensureServerRunning(project)
    val projectPath = project.getBasePath
    val globalOptionsPath = PathManager.getOptionsPath
    val dataStorageRootPath = Utils.getDataStorageRoot(
      new File(PathKt.getSystemIndependentPath(BuildManager.getInstance.getBuildSystemDirectory)),
      projectPath
    ).getCanonicalPath
    val commad = CompileServerCommand.CompileJps(
      token = "",
      projectPath = projectPath,
      globalOptionsPath = globalOptionsPath,
      dataStorageRootPath = dataStorageRootPath
    )
    val promise = Promise[Unit]

    val taskMsg = ScalaBundle.message("highlighting.compilation")
    val task: Task = new Task.Backgroundable(project, taskMsg, false) {
      override def run(indicator: ProgressIndicator): Unit = CompilerLock.get(project).withLock {
        val client = new CompilerEventGeneratingClient(project, indicator)
        val result = Try(new RemoteServerRunner(project).buildProcess(commad, client).runSync())
        promise.complete(result)
      }
      override val isHeadless: Boolean = !ApplicationManager.getApplication.isInternal
    }
    ProgressManager.getInstance.run(task)

    Await.result(promise.future, Duration.Inf)
  }
}
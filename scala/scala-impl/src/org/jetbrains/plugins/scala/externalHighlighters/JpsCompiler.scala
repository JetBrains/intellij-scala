package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.wm.ex.{StatusBarEx, WindowManagerEx}
import com.intellij.util.io.PathKt
import com.intellij.util.ui.UIUtil
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.scala.remote.CompileServerCommand
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerLock, RemoteServerRunner}
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.util.RescheduledExecutor

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Try
import org.jetbrains.plugins.scala.util.FutureUtil.sameThreadExecutionContext

trait JpsCompiler {
  def compile(): Unit
}

object JpsCompiler {

  def get(project: Project): JpsCompiler =
    ServiceManager.getService(project, classOf[JpsCompiler])
}

private class JpsCompilerImpl(project: Project)
  extends JpsCompiler {

  private val showIndicatorExecutor = new RescheduledExecutor(s"show-indicator-${project.getName}")
  
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
    val command = CompileServerCommand.CompileJps(
      token = "",
      projectPath = projectPath,
      globalOptionsPath = globalOptionsPath,
      dataStorageRootPath = dataStorageRootPath
    )
    
    val promise = Promise[Unit]
    val future = promise.future
    val taskMsg = ScalaBundle.message("highlighting.compilation")
    val task: Task.Backgroundable = new Task.Backgroundable(project, taskMsg, true) {
      override def run(indicator: ProgressIndicator): Unit = CompilerLock.get(project).withLock {
        val client = new CompilerEventGeneratingClient(project, indicator)
        val result = Try(new RemoteServerRunner(project).buildProcess(command, client).runSync())
        promise.complete(result)
      }
    }
    
    val indicator = new DeferredShowProgressIndicator(task)
    ProgressManager.getInstance.runProcessWithProgressAsynchronously(task, indicator)
    showIndicatorExecutor.schedule(ScalaHighlightingMode.compilationTimeoutToShowProgress) {
      indicator.show()
    }
    future.onComplete { _ =>
      showIndicatorExecutor.cancelLast()
    }
    Await.result(future, Duration.Inf)
  }
  
  private class DeferredShowProgressIndicator(task: Task.Backgroundable)
    extends ProgressIndicatorBase {
    
    setOwnerTask(task)

    /**
     * Shows the progress in the Status bar.
     * This method partially duplicates constructor of the 
     * [[com.intellij.openapi.progress.impl.BackgroundableProcessIndicator]].
     */
    def show(): Unit =
      if (!project.isDisposed && !project.isDefault && !ApplicationManager.getApplication.isUnitTestMode)
        for {
          frameHelper <- WindowManagerEx.getInstanceEx.findFrameHelper(project).toOption
          statusBar <- frameHelper.getStatusBar.toOption
          statusBarEx <- statusBar.asOptionOf[StatusBarEx]
        } UIUtil.invokeLaterIfNeeded(() => statusBarEx.addProgress(this, task))
  }
}
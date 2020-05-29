package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.compiler.ModuleCompilerUtil
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
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, RemoteServerRunner}
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.RescheduledExecutor

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Try
import org.jetbrains.plugins.scala.util.FutureUtil.sameThreadExecutionContext

import scala.collection.JavaConverters._

trait JpsCompiler {
  
  def compile(testScopeOnly: Boolean): Unit
  
  final def rescheduleCompilation(testScopeOnly: Boolean): Unit =
    JpsCompiler.executor.schedule(ScalaHighlightingMode.compilationDelay) {
      compile(testScopeOnly)
    }
  
  def cancel(): Unit
  
  def isRunning: Boolean
}

object JpsCompiler {

  private val executor = new RescheduledExecutor("CompileJpsExecutor")
  
  def get(project: Project): JpsCompiler =
    ServiceManager.getService(project, classOf[JpsCompiler])
}

private class JpsCompilerImpl(project: Project)
  extends JpsCompiler {

  private val showIndicatorExecutor = new RescheduledExecutor(s"ShowIndicator-${project.getName}")
  @volatile private var progressIndicator: Option[ProgressIndicator] = None
  
  // SCL-17295
  @Cached(ProjectRootManager.getInstance(project), null)
  private def saveProjectOnce(): Unit = project.save()

  override def compile(testScopeOnly: Boolean): Unit = {
    saveProjectOnce()
    CompileServerLauncher.ensureServerRunning(project)

    val projectPath = project.getBasePath
    val globalOptionsPath = PathManager.getOptionsPath
    val dataStorageRootPath = Utils.getDataStorageRoot(
      new File(PathKt.getSystemIndependentPath(BuildManager.getInstance.getBuildSystemDirectory)),
      projectPath
    ).getCanonicalPath
    val sortedModules = ModuleCompilerUtil
      .getSortedModuleChunks(project, project.modules.asJava).asScala
      .flatMap { chunk =>
        val modules = chunk.getNodes
        if (modules.size > 1) throw new IllegalStateException(s"More than one module in the chunk: $modules")
        modules.asScala
      }
      .map(_.getName)
    val command = CompileServerCommand.CompileJps(
      token = "",
      projectPath = projectPath,
      sortedModules = sortedModules,
      testScopeOnly = testScopeOnly,
      globalOptionsPath = globalOptionsPath,
      dataStorageRootPath = dataStorageRootPath
    )

    val promise = Promise[Unit]
    val future = promise.future
    val taskMsg = ScalaBundle.message("highlighting.compilation")
    val task: Task.Backgroundable = new Task.Backgroundable(project, taskMsg, true) {
      override def run(indicator: ProgressIndicator): Unit = CompilerLock.get(project).withLock {
        progressIndicator = Some(indicator)
        val client = new CompilerEventGeneratingClient(project, indicator)
        val result = Try(new RemoteServerRunner(project).buildProcess(command, client).runSync())
        progressIndicator = None
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
  
  override def cancel(): Unit =
    progressIndicator.foreach(_.cancel())

  override def isRunning: Boolean =
    progressIndicator.isDefined
  
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
package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.openapi.wm.ex.{StatusBarEx, WindowManagerEx}
import com.intellij.util.io.PathKt
import com.intellij.util.ui.UIUtil
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.CompileServerCommand
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.{CompileServerClient, CompileServerLauncher}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.FutureUtil.sameThreadExecutionContext
import org.jetbrains.plugins.scala.util.RescheduledExecutor

import scala.annotation.nowarn
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Try

/**
 * For compiler-based-highlighting
 */
trait HighlightingCompiler {
  def rescheduleCompilation(delayedProgressShow: Boolean,
                            forceCompileFile: Option[VirtualFile] = None): Unit
  
  def cancel(): Unit
  
  def isRunning: Boolean
}

object HighlightingCompiler {

  def get(project: Project): HighlightingCompiler =
    ServiceManager.getService(project, classOf[HighlightingCompiler])
}

class HighlightingCompilerImpl(project: Project)
  extends HighlightingCompiler
    with Disposable {

  private val executor = new RescheduledExecutor("CompileJpsExecutor", this)
  private val showIndicatorExecutor = new RescheduledExecutor(s"ShowIndicator-${project.getName}", this)

  @volatile private var progressIndicator: Option[ProgressIndicator] = None
  private val modTracker = new SimpleModificationTracker

  // SCL-17295
  @nowarn("msg=pure expression")
  @Cached(modTracker, null)
  private def saveProjectOnce(): Unit =
    if (!project.isDisposed || project.isDefault) project.save()

  override def rescheduleCompilation(delayedProgressShow: Boolean,
                                     forceCompileFile: Option[VirtualFile]): Unit =
    executor.schedule(ScalaHighlightingMode.compilationDelay) {
      compile(delayedProgressShow, forceCompileFile)
    }

  private def compile(delayedProgressShow: Boolean,
                      forceCompileFile: Option[VirtualFile]): Unit = {
    saveProjectOnce()
    CompileServerLauncher.ensureServerRunning(project)

    val projectPath = Option(project.getPresentableUrl)
      .map(VirtualFileManager.extractPath)
      .getOrElse(throw new IllegalStateException("Can't determine project path"))
    val globalOptionsPath = PathManager.getOptionsPath
    val dataStorageRootPath = Utils.getDataStorageRoot(
      new File(PathKt.getSystemIndependentPath(BuildManager.getInstance.getBuildSystemDirectory)),
      projectPath
    ).getCanonicalPath
    val command = CompileServerCommand.CompileJps(
      projectPath = projectPath,
      globalOptionsPath = globalOptionsPath,
      dataStorageRootPath = dataStorageRootPath
    )

    val promise = Promise[Unit]
    val taskMsg = ScalaBundle.message("highlighting.compilation")
    val task: Task.Backgroundable = new Task.Backgroundable(project, taskMsg, true) {
      override def run(indicator: ProgressIndicator): Unit = CompilerLock.get(project).withLock {
        progressIndicator = Some(indicator)
        val result = Try {
          val client = new CompilerEventGeneratingClient(project, indicator)
          val compileServerClient = CompileServerClient.get(project)
          compileServerClient.execCommand(command, client)
          doForceCompileFile(forceCompileFile, client)
        }
        progressIndicator = None
        promise.complete(result)
      }
    }
    
    val indicator = new DeferredShowProgressIndicator(task)
    ProgressManager.getInstance.runProcessWithProgressAsynchronously(task, indicator)
    val showProgressDelay = if (delayedProgressShow)
      ScalaHighlightingMode.compilationTimeoutToShowProgress
    else
      Duration.Zero
    showIndicatorExecutor.schedule(showProgressDelay) {
      indicator.show()
    }

    val future = promise.future
    future.onComplete { _ =>
      showIndicatorExecutor.cancelLast()
    }
    Await.result(future, Duration.Inf)
  }
  
  private def doForceCompileFile(fileOption: Option[VirtualFile],
                                 client: Client): Unit = {
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    for {
      file <- fileOption
      module <- Option(projectFileIndex.getModuleForFile(file))
    } OneFileCompiler.compile(file.toFile, module, client)
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

  override def dispose(): Unit = {
    progressIndicator = None
  }
}
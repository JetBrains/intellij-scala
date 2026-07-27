package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.internal.ScalaDynamicPluginManager

import java.util.concurrent.ExecutorService

@Service(Array(Service.Level.PROJECT))
private final class BackgroundExecutorService(project: Project) extends Disposable {

  val executor: ExecutorService = {
    val backendExecutor = AppExecutorUtil.getAppExecutorService
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Scala Compiler Based Highlighting background executor", backendExecutor, 1, this)
  }

  private def executeOnBackgroundThread(runnable: Runnable): Unit = {
    executor.execute(runnable)
  }

  override def dispose(): Unit = {}
}

private object BackgroundExecutorService {
  def executeOnBackgroundThreadInNotDisposed(project: Project)(action: => Unit): Unit = {
    if (ScalaDynamicPluginManager.isScalaPluginUnloading)
      return

    instance(project).executeOnBackgroundThread(() => {
      if (!project.isDisposed) {
        action
      }
    })
  }

  def instance(project: Project): BackgroundExecutorService =
    project.getService[BackgroundExecutorService](classOf[BackgroundExecutorService])
}

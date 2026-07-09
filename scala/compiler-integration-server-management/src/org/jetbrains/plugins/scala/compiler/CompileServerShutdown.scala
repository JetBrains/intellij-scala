package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{Project, ProjectCloseListener}
import com.intellij.platform.eel.provider.utils.EelProjectUtils
import org.jetbrains.plugins.scala.util.ScalaShutDownTracker

import scala.concurrent.duration.Duration

private object CompileServerShutdown {

  private final val Log: Logger = Logger.getInstance(classOf[CompileServerShutdown.type])

  @Service(Array(Service.Level.APP))
  private final class RegisterShutdownTaskService extends Disposable {
    // Guaranteed to run at most-once, when the service is initialized.
    ScalaShutDownTracker.registerShutdownTask(() => {
      Log.info("Shutdown event triggered, stopping server")
      CompileServerLauncher.stopServerAndWaitFor(Duration.Zero)
    })

    ApplicationManager.getApplication.getMessageBus.connect(this).subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener {
      override def projectClosing(project: Project): Unit = {
        //noinspection ApiStatus,UnstableApiUsage
        val local = EelProjectUtils.isProjectLocal(project)
        if (!local) {
          // Only stop the server if the remote eel project is closing. We don't want to leak the process running
          // in a remote eel with no option to stop it after the project is closed.
          CompileServerLauncher.stopServerAndWaitFor(Duration.Zero)
        }
      }
    })

    override def dispose(): Unit = {}
  }

  def registerShutdownTask(): Unit =
    ApplicationManager.getApplication.getService(classOf[RegisterShutdownTaskService])
}

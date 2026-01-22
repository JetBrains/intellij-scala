package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.util.ScalaShutDownTracker

import scala.concurrent.duration.Duration

private object CompileServerShutdown {

  private final val Log: Logger = Logger.getInstance(classOf[CompileServerShutdown.type])

  @Service(Array(Service.Level.APP))
  private final class RegisterShutdownTaskService {
    // Guaranteed to run at most-once, when the service is initialized.
    ScalaShutDownTracker.registerShutdownTask(() => {
      Log.info("Shutdown event triggered, stopping server")
      CompileServerLauncher.stopServerAndWaitFor(Duration.Zero)
    })
  }

  def registerShutdownTask(): Unit =
    ApplicationManager.getApplication.getService(classOf[RegisterShutdownTaskService])
}

package org.jetbrains.sbt.shell.communication

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.shell.communication.SbtShellQueuedStartupOutputMirroring.{Owner, Registration}

import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the temporary process listener that mirrors sbt shell startup output into the request waiting for that startup.
 */
private[shell] final class SbtShellQueuedStartupOutputMirroring(project: Project) {

  private val log = Logger.getInstance(classOf[SbtShellQueuedStartupOutputMirroring])
  private val registrationRef = new AtomicReference[Registration](null)

  def registerIfNeeded(handler: OSProcessHandler, owner: Option[Owner]): Unit = {
    remove()

    owner.foreach { owner =>
      val startupOutputListener = new SbtShellQueuedStartupOutputListener(
        new SbtShellModeProviderImpl(project),
        owner.onOutputLine,
      )
      val registration = Registration(owner.requestId, handler, startupOutputListener)

      if (registrationRef.compareAndSet(null, registration)) {
        handler.addProcessListener(startupOutputListener)
        log.debug(s"Installed queued startup output listener: requestId=${registration.requestId}")
      }
    }
  }

  def remove(requestId: Option[SbtShellCommandRequestId] = None): Unit = {
    val registration = registrationRef.get()
    if (registration != null && requestId.forall(_ == registration.requestId)) {
      if (registrationRef.compareAndSet(registration, null)) {
        registration.handler.removeProcessListener(registration.listener)
        log.debug(s"Removed queued startup output listener: requestId=${registration.requestId}")
      }
    }
  }
}

private[shell] object SbtShellQueuedStartupOutputMirroring {
  final case class Owner(
    requestId: SbtShellCommandRequestId,
    onOutputLine: String => Unit,
  )

  private final case class Registration(
    requestId: SbtShellCommandRequestId,
    handler: OSProcessHandler,
    listener: SbtShellQueuedStartupOutputListener,
  )
}

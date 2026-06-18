package org.jetbrains.sbt.shell.communication

import org.jetbrains.annotations.ApiStatus.Experimental

import java.util.UUID

/**
 * Identifies one command request inside the sbt shell communication queues.
 *
 * This id is a request handle used to correlate queue entries, log messages, and cancellation requests.
 *
 * Callers that need cancellation should create an [[SbtShellCommandRequest]],
 * pass it to [[SbtShellCommandSubmitter.run]],
 * and later pass [[SbtShellCommandRequest.requestId]] to [[SbtShellCommunication.removeCommandFromQueueOrCancel]]
 *
 * @note it is NOT a build id, execution session id, build-event id, or process id.
 */
@Experimental
opaque type SbtShellCommandRequestId = String

@Experimental
object SbtShellCommandRequestId {
  def apply(value: String): SbtShellCommandRequestId = value

  def random(): SbtShellCommandRequestId =
    SbtShellCommandRequestId(UUID.randomUUID().toString)

  extension (requestId: SbtShellCommandRequestId)
    def value: String = requestId
}

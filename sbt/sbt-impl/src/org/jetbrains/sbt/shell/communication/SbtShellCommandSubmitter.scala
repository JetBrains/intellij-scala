package org.jetbrains.sbt.shell.communication

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext

import scala.concurrent.Future


/**
 * ATTENTION: Originally this interface was introduced to be able to mock it in tests.
 * However, later it was decided that we will mock the SBT JVM process itself.
 * Still, this interface is useful to have to highlight a separate arthitectural component.
 */
@Experimental
trait SbtShellCommandSubmitter {

  /**
   * Queue an sbt command for execution in the sbt shell.
   *
   * @param request all options needed to submit and process the shell command.
   * @tparam Result the result type produced by shell-event processing.
   * @return a future completed with the processed command result.
   */
  def run[Result](request: SbtShellCommandRequest[Result]): Future[Result]

  /**
   * Queue an sbt command request and return the entire shell output.
   */
  final def runAndCollectOutput(
    request: SbtShellCommandRequest[StringBuilder]
  ): Future[String] = {
    run(request).map(_.toString())
  }

  /**
   * Queue an sbt command and return the entire shell output.
   */
  final def runAndCollectOutput(@NonNls sbtCommandText: => String): Future[String] =
    runAndCollectOutput(SbtShellCommandRequest.collectOutput(sbtCommandText))

  /**
   * Cancel a command that was previously submitted through this submitter.
   *
   * Implementations should remove queued requests when possible and interrupt the running shell command otherwise.
   */
  def cancel(requestId: SbtShellCommandRequestId): Unit
}

@Experimental
object SbtShellCommandSubmitter {
  /**
   * Returns the project-level sbt shell command submitter.
   *
   * In production this is backed by [[org.jetbrains.sbt.shell.SbtShellCommunication]]
   *
   * It can be mocked in tests
   */
  def instance(project: Project): SbtShellCommandSubmitter =
    project.getService(classOf[SbtShellCommandSubmitter])
}

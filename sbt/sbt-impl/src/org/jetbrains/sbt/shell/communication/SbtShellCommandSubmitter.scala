package org.jetbrains.sbt.shell.communication

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext

import scala.concurrent.Future

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

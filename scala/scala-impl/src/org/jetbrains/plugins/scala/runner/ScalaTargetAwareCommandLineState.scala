package org.jetbrains.plugins.scala.runner

import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.process.{KillableColoredProcessHandler, OSProcessHandler, ProcessTerminatedListener}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ScalaTargetAwareCommandLineState(env: ExecutionEnvironment) extends JavaCommandLineState(env) {

  /**
   * @note This is a simplified version of `JavaTestFrameworkRunnableState#createHandler`.
   *       Calling `getEnvironment.getPreparedTargetEnvironment` and `getTargetedCommandLine`
   *       sets up the run configuration for a remote execution target, such as eel/WSL.
   *       It handles automatic translation of the run configuration parameters to match
   *       the expectations of the target machine.
   */
  protected def createHandler(): OSProcessHandler = {
    val remoteEnvironment = getEnvironment.getPreparedTargetEnvironment(this, TargetProgressIndicator.EMPTY)
    val targetedCommandLineBuilder = getTargetedCommandLine
    val targetedCommandLine = targetedCommandLineBuilder.build()

    val process = remoteEnvironment.createProcess(targetedCommandLine, new EmptyProgressIndicator())

    val processHandler = new KillableColoredProcessHandler.Silent(
      process,
      targetedCommandLine.getCommandPresentation(remoteEnvironment),
      targetedCommandLine.getCharset,
      targetedCommandLineBuilder.getFilesToDeleteOnTermination
    )

    ProcessTerminatedListener.attach(processHandler)
    processHandler
  }
}

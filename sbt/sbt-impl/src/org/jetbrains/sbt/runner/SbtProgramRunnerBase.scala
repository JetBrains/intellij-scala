package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.sbt.shell.SbtShellCommunication.{Output, ShellEvent}
import org.jetbrains.sbt.shell.{SbtShellCommunication, SbtShellToolWindowFactory}

import scala.concurrent.Future

trait SbtProgramRunnerBase {

  /**
   * @return a future with all the output collected during the command execution
   */
  @RequiresBackgroundThread
  protected def submitCommands(
    env: ExecutionEnvironment,
    state: SbtCommandLineState,
  ): Future[java.lang.CharSequence] = {
    val project = env.getProject

    // When running sbt run configuration show sbt shell if it's hidden
    invokeLater {
      showSbtToolwindow(project)
    }

    val sbtCommunication = SbtShellCommunication.forProject(project)
    val commands = state.processedCommands

    val listener = state.getListener.getOrElse((_: String) => ())

    // Q: what is this builder needed for anyway? It seems to be ignored
    val eventHandler = (builder: StringBuilder, event: ShellEvent) => {
      event match {
        case Output(line) =>
          listener.apply(line)
          builder.append("\n").append(line)
        case _ =>
          builder
      }
    }

    sbtCommunication.command(commands, new StringBuilder(), eventHandler)
  }

  protected def isSbtRunConfigurationWithUseSbtShell(profile: RunProfile): Boolean = profile match {
    case sbtConf: SbtRunConfiguration =>
      sbtConf.useSbtShell
    case _ =>
      false
  }

  private def showSbtToolwindow(project: Project): Unit = {
    val toolwindow = ToolWindowManager.getInstance(project).getToolWindow(SbtShellToolWindowFactory.ID)
    if (toolwindow != null) {
      toolwindow.show()
    }
  }
}

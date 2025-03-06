package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.statistics.SbtShellCommandsUsagesCollector.Group

/**
 * Note, other sbt-related info is collected in these collectors:
 *  - [[org.jetbrains.plugins.scala.statistics.SbtSettingsCollector]]
 *  - [[org.jetbrains.plugins.scala.statistics.ScalaProjectStateCollector]]
 *  - [[org.jetbrains.plugins.scala.statistics.ScalaProjectSettingsCollector]]
 */
//noinspection UnstableApiUsage
class SbtShellCommandsUsagesCollector extends CounterUsagesCollector {
  override def getGroup: EventLogGroup = Group
}

object SbtShellCommandsUsagesCollector {
  /**
   * See also [[SbtSettingsCollector.Group]]
   *
   * @todo once AP-4992 is implemented, we can rename the group to "build.sbt.actions"
   *       (to be consistent with SbtSettingsCollector.Group and GradleActionsUsagesCollector.GROUP)
   */
  private val Group = new EventLogGroup("scala.sbt", 1)

  private val ShellCommandEvent = Group.registerEvent("shell.execute.command")
  private val ShellTestCommandEvent = Group.registerEvent("shell.test.command")
  private val ShellTestRunCommandEvent = Group.registerEvent("shell.test.run.command")

  def logShellCommand(project: Project): Unit = ShellCommandEvent.log(project)
  def logShellTestCommand(project: Project): Unit = ShellTestCommandEvent.log(project)
  def logShellTestRunCommand(project: Project): Unit = ShellTestRunCommandEvent.log(project)
}

package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.statistics.ScalaSbtUsagesCollector.Group

//noinspection UnstableApiUsage
class ScalaSbtUsagesCollector extends CounterUsagesCollector {
  override def getGroup: EventLogGroup = Group
}

object ScalaSbtUsagesCollector {
  private val Group = new EventLogGroup("scala.sbt", 1)

  private val ShellCommandEvent = Group.registerEvent("shell.execute.command")
  private val ShellTestCommandEvent = Group.registerEvent("shell.test.command")
  private val ShellTestRunCommandEvent = Group.registerEvent("shell.test.run.command")

  def logShellCommand(project: Project): Unit = ShellCommandEvent.log(project)
  def logShellTestCommand(project: Project): Unit = ShellTestCommandEvent.log(project)
  def logShellTestRunCommand(project: Project): Unit = ShellTestRunCommandEvent.log(project)
}

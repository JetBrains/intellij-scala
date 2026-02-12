package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.statistics.ScalaJsUsagesCollector.Group

//noinspection UnstableApiUsage
class ScalaJsUsagesCollector extends CounterUsagesCollector {
  override def getGroup: EventLogGroup = Group
}

object ScalaJsUsagesCollector {
  private val Group = new EventLogGroup("scala.js", 1) // TODO: SCL-24479

  private val DynamicResolveEvent = Group.registerEvent("dynamic.resolve") // TODO: SCL-24479
  private val DynamicCompletionEvent = Group.registerEvent("dynamic.completion") // TODO: SCL-24479

  def logDynamicResolve(project: Project): Unit = DynamicResolveEvent.log(project)
  def logDynamicCompletion(project: Project): Unit = DynamicCompletionEvent.log(project)
}

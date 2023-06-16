package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaProjectSettings}
import org.jetbrains.plugins.scala.statistics.ScalaProjectSettingsCollector._
import org.jetbrains.sbt.settings.SbtSettings

import java.util

//noinspection UnstableApiUsage
class ScalaProjectSettingsCollector extends ProjectUsagesCollector {
  override def getGroup: EventLogGroup = Group

  override def getMetrics(project: Project): util.Set[MetricEvent] = {
    val result = new util.HashSet[MetricEvent]

    if (!project.hasScala) return result

    def addUsageIf(condition: Boolean, event: EventId): Unit = {
      if (condition) result.add(event.metric())
    }

    def addUsage(event: EventId): Unit = addUsageIf(condition = true, event)

    val modules = project.modules
    val sbtSettings = SbtSettings.getInstance(project)
    val sbtProjectSettings = modules.map(sbtSettings.getLinkedProjectSettings).flatten
    val compilerSettings = ScalaCompilerConfiguration.instanceIn(project)
    val projectSettings = ScalaProjectSettings.getInstance(project)

    val isSbtProject = sbtProjectSettings.nonEmpty
    val isSbtShellBuild = sbtProjectSettings.exists(_.useSbtShellForBuild)

    addUsageIf(isSbtProject && isSbtShellBuild, SbtShellBuildEvent)
    addUsageIf(isSbtProject && !isSbtShellBuild, SbtIdeaBuildEvent)

    if (!isSbtShellBuild) {
      val incType = compilerSettings.incrementalityType match {
        case IncrementalityType.SBT => CompilerIncTypeUsedSbtEvent
        case IncrementalityType.IDEA => CompilerIncTypeUsedIdeaEvent
      }
      val compileServerEnabled = ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED
      addUsage(incType)
      addUsageIf(compileServerEnabled, CompilerCompileServerUsedEvent)
    }

    addUsageIf(projectSettings.isProjectViewHighlighting, ProjectViewHighlightingEvent)

    result
  }
}

//noinspection UnstableApiUsage
private object ScalaProjectSettingsCollector {
  private val Group = new EventLogGroup("scala.project.settings", 1)

  private val SbtShellBuildEvent = Group.registerEvent("sbt.shell.build")
  private val SbtIdeaBuildEvent = Group.registerEvent("sbt.idea.build")
  private val CompilerIncTypeUsedSbtEvent = Group.registerEvent("compiler.inc.type.used.sbt")
  private val CompilerIncTypeUsedIdeaEvent = Group.registerEvent("compiler.inc.type.used.idea")
  private val CompilerCompileServerUsedEvent = Group.registerEvent("compiler.compile.server.used")
  private val ProjectViewHighlightingEvent = Group.registerEvent("project.view.highlighting")
}

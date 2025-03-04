package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.statistics.SbtSettingsCollector.Events
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings

import java.util.Collections
import java.{util => ju}
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

//TODO: move it to sbt module or some "FUS" module
/**
 * Note, other sbt-related info is collected in these collectors:
 *  - [[org.jetbrains.plugins.scala.statistics.SbtShellCommandsUsagesCollector]]
 *  - [[org.jetbrains.plugins.scala.statistics.ScalaProjectStateCollector]]
 *  - [[org.jetbrains.plugins.scala.statistics.ScalaProjectSettingsCollector]]
 *
 * @note The initial implementation of the class was inspired from
 *       [[org.jetbrains.plugins.gradle.statistics.GradleSettingsCollector]]<br>
 *       Note that not all events from the Gradle collector seem to be needed.
 *       E.g. "hasGradleProject" seems redundant as we can see this value in multiple other sources:
 *       - In "Sample size" in "Applied event or filters"
 *       - In "External System Id" event of the "Build tools" Group
 */
//noinspection ScalaUnusedSymbol,UnstableApiUsage,ApiStatus
class SbtSettingsCollector extends ProjectUsagesCollector {
  override def getGroup: EventLogGroup = SbtSettingsCollector.Group

  override def getMetrics(project: Project): ju.Set[MetricEvent] = {
    val gradleSettings = SbtSettings.getInstance(project)
    val linkedProjectsSettings = gradleSettings.getLinkedProjectsSettings.asScala
    if (linkedProjectsSettings.isEmpty)
      return Collections.emptySet

    val usages = new ju.HashSet[MetricEvent]

    ignoreSbtSettingsMetrics(usages, gradleSettings)

    // project settings
    for (projectSettings <- linkedProjectsSettings) {
      collectSbtProjectSettingsMetrics(usages, project, projectSettings)
    }

    usages
  }

  /**
   * Note, this method does nothing and only exists for the documentation peruses.
   *
   * Global settings [[SbtSettings]] are already recorded as a part of
   * [[com.intellij.configurationStore.statistic.eventLog.SettingsCollector]].
   * They are recorded per IDE installation (~ per User) (not per-project). However, this is enough for us.
   * Right now, there are no settings in [[[[SbtSettings]] that we would like to track per project
   */
  private def ignoreSbtSettingsMetrics(usages: ju.Set[MetricEvent], gradleSettings: SbtSettings): Unit = {
    // do nothing (see scala doc)
  }

  private def collectSbtProjectSettingsMetrics(usages: ju.Set[MetricEvent], project: Project, projectSettings: SbtProjectSettings): Unit = {
    usages.add(Events.ResolveClassifiers.metric(projectSettings.resolveClassifiers))
    usages.add(Events.ResolveSbtClassifiers.metric(projectSettings.resolveSbtClassifiers))
    usages.add(Events.PreferScala2.metric(projectSettings.preferScala2))
    usages.add(Events.UseSeparateCompilerOutputPaths.metric(projectSettings.useSeparateCompilerOutputPaths))
    usages.add(Events.SeparateProdAndTestSources.metric(projectSettings.separateProdAndTestSources))
    usages.add(Events.UseSbtShellForImport.metric(projectSettings.useSbtShellForImport))
    usages.add(Events.UseSbtShellForBuild.metric(projectSettings.useSbtShellForBuild))
    usages.add(Events.EnableDebugSbtShell.metric(projectSettings.enableDebugSbtShell))

    usages.add(Events.SbtVersion.metric(projectSettings.sbtVersion))
    val sbtVersionMajor = Try(Version(projectSettings.sbtVersion).major(2)).getOrElse("0.0") // 0.0 ~ invalid version
    usages.add(Events.SbtVersionMajor.metric(projectSettings.sbtVersion))

    //"projectSettings.jdk" is ignored because it's only for the new project wizard
  }
}

//noinspection UnstableApiUsage
private object SbtSettingsCollector {
  // The group name was chosen to be unified with GradleSettingsCollector.GROUP
  private val Group = new EventLogGroup("build.sbt.state", 1)

  //noinspection TypeAnnotation
  private object Events {
    val ResolveClassifiers = Group.registerEvent("resolveClassifiers", EventFields.Enabled)
    val ResolveSbtClassifiers = Group.registerEvent("resolveSbtClassifiers", EventFields.Enabled)
    val PreferScala2 = Group.registerEvent("preferScala2", EventFields.Enabled)
    val UseSeparateCompilerOutputPaths = Group.registerEvent("useSeparateCompilerOutputPaths", EventFields.Enabled)
    val SeparateProdAndTestSources = Group.registerEvent("separateProdAndTestSources", EventFields.Enabled)
    val UseSbtShellForImport = Group.registerEvent("useSbtShellForImport", EventFields.Enabled)
    val UseSbtShellForBuild = Group.registerEvent("useSbtShellForBuild", EventFields.Enabled)
    val EnableDebugSbtShell = Group.registerEvent("enableDebugSbtShell", EventFields.Enabled)

    /**
     * Collects the full sbt version (e.g., 1.10.7, 2.0.0-M3)
     *
     * @note this event is supposed to replace [[org.jetbrains.plugins.scala.statistics.ScalaProjectStateCollector.SbtInfoEvent]]
     */
    val SbtVersion = Group.registerEvent("sbtVersion", EventFields.StringValidatedByRegexpReference("version", "version"))

    /**
     * Collects the first 2 digits of the sbt version (e.g., 1.10. 0.13, 2.0)
     *
     * @note a separate event for the major version might be redundant, but it's convenient to view this info
     *       in FUS directly, without the need of do extra processing of a full version
     */
    val SbtVersionMajor = Group.registerEvent("sbtVersionMajor", EventFields.StringValidatedByRegexpReference("versionMajor", "version"))
  }
}
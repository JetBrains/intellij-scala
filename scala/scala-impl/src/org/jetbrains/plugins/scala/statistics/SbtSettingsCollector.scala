package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.{BooleanEventField, EventFields, EventPair, StringEventField}
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.statistics.SbtSettingsCollector.{Events, Fields}
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
    val sbtVersionMajor: String = Try(Version(projectSettings.sbtVersion).major(2)).map(_.presentation).getOrElse("0.0") // 0.0 ~ invalid version
    usages.add(Events.SbtProjectSettings.metric(
      Array(
        //NOTE: "projectSettings.jdk" is ignored because it's only for the new project wizard
        new EventPair(Fields.ResolveClassifiers, projectSettings.resolveClassifiers: java.lang.Boolean),
        new EventPair(Fields.ResolveSbtClassifiers, projectSettings.resolveSbtClassifiers: java.lang.Boolean),
        new EventPair(Fields.PreferScala2, projectSettings.preferScala2: java.lang.Boolean),
        new EventPair(Fields.UseSeparateCompilerOutputPaths, projectSettings.useSeparateCompilerOutputPaths: java.lang.Boolean),
        new EventPair(Fields.SeparateProdAndTestSources, projectSettings.separateProdAndTestSources: java.lang.Boolean),
        new EventPair(Fields.UseSbtShellForImport, projectSettings.useSbtShellForImport: java.lang.Boolean),
        new EventPair(Fields.UseSbtShellForBuild, projectSettings.useSbtShellForBuild: java.lang.Boolean),
        new EventPair(Fields.EnableDebugSbtShell, projectSettings.enableDebugSbtShell: java.lang.Boolean),
        new EventPair(Fields.SbtVersion, projectSettings.sbtVersion),
        new EventPair(Fields.SbtVersionMajor, sbtVersionMajor),
      ): _*
    ))
  }
}

//noinspection UnstableApiUsage
private object SbtSettingsCollector {
  // The group name was chosen to be unified with GradleSettingsCollector.GROUP
  private val Group = new EventLogGroup("build.sbt.state", 1) // TODO: SCL-24479

  locally {
    //initialize the fields eagerly in order scheme generation works
    //when running the idea with "buildEventsScheme --outputFile=scheme.json --pluginId=org.intellij.scala" arguments
    //otherwise we will get an error "Group should contain at least one event"
    Events
  }

  //noinspection TypeAnnotation
  private object Events {
    val SbtProjectSettings = Group.registerVarargEvent(
      "sbt.project.settings",
      Array(
        Fields.ResolveClassifiers,
        Fields.ResolveSbtClassifiers,
        Fields.PreferScala2,
        Fields.UseSeparateCompilerOutputPaths,
        Fields.SeparateProdAndTestSources,
        Fields.UseSbtShellForImport,
        Fields.UseSbtShellForBuild,
        Fields.EnableDebugSbtShell,
        Fields.SbtVersion,
        Fields.SbtVersionMajor
      ): _*
    ) // TODO: SCL-24479
  }

  private object Fields {
    val ResolveClassifiers: BooleanEventField =  EventFields.Boolean("resolve_classifiers")
    val ResolveSbtClassifiers: BooleanEventField =  EventFields.Boolean("resolve_sbt_classifiers")
    val PreferScala2: BooleanEventField =  EventFields.Boolean("prefer_scala2")
    val UseSeparateCompilerOutputPaths: BooleanEventField =  EventFields.Boolean("use_separate_compiler_output_paths")
    val SeparateProdAndTestSources: BooleanEventField =  EventFields.Boolean("separate_prod_and_test_sources")
    val UseSbtShellForImport: BooleanEventField =  EventFields.Boolean("use_sbt_shell_for_import")
    val UseSbtShellForBuild: BooleanEventField =  EventFields.Boolean("use_sbt_shell_for_build")
    val EnableDebugSbtShell: BooleanEventField =  EventFields.Boolean("enable_debug_sbt_shell")

    /**
     * Stores the full sbt version (e.g., 1.10.7, 2.0.0-M3)
     *
     * @note this event is supposed to replace [[org.jetbrains.plugins.scala.statistics.ScalaProjectStateCollector.SbtInfoEvent]]
     */
    val SbtVersion: StringEventField = EventFields.StringValidatedByRegexpReference("sbt_version", "version")

    /**
     * Stores the first 2 digits of the sbt version (e.g., 1.10. 0.13, 2.0)
     *
     * @note a separate event for the major version might be redundant, but it's convenient to view this info
     *       in FUS directly, without the need of do extra processing of a full version
     */
    val SbtVersionMajor: StringEventField = EventFields.StringValidatedByRegexpReference("sbt_version_major", "version")
  }
}
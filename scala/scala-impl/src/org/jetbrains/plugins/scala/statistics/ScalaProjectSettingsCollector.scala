package org.jetbrains.plugins.scala.statistics

import java.util

import com.intellij.internal.statistic.AbstractProjectsUsagesCollector
import com.intellij.internal.statistic.beans.{GroupDescriptor, UsageDescriptor}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.settings.SbtSettings

/**
  * Nikolay.Tropin
  * 24-Nov-17
  */
class ScalaProjectSettingsCollector extends AbstractProjectsUsagesCollector {
  override def getProjectUsages(project: Project): util.Set[UsageDescriptor] = {
    val result = new util.HashSet[UsageDescriptor]

    if (!project.hasScala) return result

    def addUsageIf(condition: Boolean, key: String): Unit = {
      if (condition) result.add(new UsageDescriptor(key))
    }

    def addUsage(key: String): Unit = addUsageIf(condition = true, key)


    val modules = project.modules
    val sbtSettings = SbtSettings.getInstance(project)
    val sbtProjectSettings = modules.map(sbtSettings.getLinkedProjectSettings).flatten
    val compilerSettings = ScalaCompilerConfiguration.instanceIn(project)
    val projectSettings = ScalaProjectSettings.getInstance(project)

    val isSbtProject = sbtProjectSettings.nonEmpty
    val isSbtShellBuild = sbtProjectSettings.exists(_.useSbtShellForBuild)

    addUsageIf(isSbtProject && isSbtShellBuild, "scala.sbt.shell.build")
    addUsageIf(isSbtProject && !isSbtShellBuild, "scala.sbt.idea.build")

    if (!isSbtShellBuild) {
      val incType = compilerSettings.incrementalityType.name()
      val compileServerEnabled = ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED

      addUsage(s"scala.compiler.inc.type.used.$incType")
      addUsageIf(compileServerEnabled, "scala.compiler.compile.server.used")
    }

    addUsageIf(projectSettings.isProjectViewHighlighting, "scala.project.view.highlighting")
    

    result
  }

  override def getGroupId: GroupDescriptor = GroupDescriptor.create("Scala Settings")
}

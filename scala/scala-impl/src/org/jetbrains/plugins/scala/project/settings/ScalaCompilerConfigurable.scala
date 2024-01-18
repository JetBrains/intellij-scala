package org.jetbrains.plugins.scala
package project
package settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.options.Configurable.Composite
import com.intellij.openapi.options.{Configurable, SearchableConfigurable}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector

import javax.swing.JPanel

/**
 * @see [[org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState]]
 */
class ScalaCompilerConfigurable(project: Project)
  extends SearchableConfigurable
    with Configurable.NoScroll
    with Composite {

  override def getId: String = ScalaCompilerConfigurable.Id

  //NOTE: duplicated in XML file
  override def getDisplayName: String = ScalaBundle.message("displayname.scala.compiler")

  private val form = new ScalaCompilerConfigurationPanel(project)

  private def configuration = ScalaCompilerConfiguration.instanceIn(project)

  private val profilesPanel: ScalaCompilerProfilesPanel = form.getProfilesPanel

  override def createComponent(): JPanel = form.getContentPanel

  override def isModified: Boolean = {
    if (form.getIncrementalityType != configuration.incrementalityType)
      return true
    if (!equalSettings(profilesPanel.getDefaultProfile, configuration.defaultProfile))
      return true
    if (!profilesPanel.getModuleProfiles.corresponds(configuration.customProfiles)(equalSettings))
      return true

    false
  }

  private def equalSettings(profile1: ScalaCompilerSettingsProfile, profile2: ScalaCompilerSettingsProfile): Boolean =
    settingsState(profile1) == settingsState(profile2)

  private def settingsState(profile: ScalaCompilerSettingsProfile): ScalaCompilerSettingsState =
    profile.getSettings.toState

  override def reset(): Unit = {
    form.setIncrementalityType(configuration.incrementalityType)
    profilesPanel.initProfiles(configuration.defaultProfile, configuration.customProfiles)
  }

  override def apply(): Unit = {
    val newIncType = form.getIncrementalityType
    if (newIncType != configuration.incrementalityType) {
      ScalaActionUsagesCollector.logIncrementalityTypeSet(newIncType, project)
    }

    configuration.incrementalityType = newIncType
    configuration.defaultProfile = profilesPanel.getDefaultProfile.copy
    configuration.customProfiles = profilesPanel.getModuleProfiles.map(_.copy)
    if (!project.isDefault) {
      DaemonCodeAnalyzer.getInstance(project).restart()
      BuildManager.getInstance().clearState(project)
    }
  }

  override def getConfigurables: Array[Configurable] = Array()

  override def getHelpTopic: String =
    ScalaWebHelpProvider.HelpPrefix + "compile-and-build-scala-projects.html"
}

object ScalaCompilerConfigurable {
  //NOTE: duplicated in XML file
  val Id = "scala.compiler"
}
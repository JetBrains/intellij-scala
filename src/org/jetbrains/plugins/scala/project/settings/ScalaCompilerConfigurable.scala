package org.jetbrains.plugins.scala.project.settings

import javax.swing.JComponent

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.AbstractConfigurable

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
class ScalaCompilerConfigurable(project: Project, configuration: ScalaCompilerConfiguration) extends AbstractConfigurable("Scala Compiler")  {
  private val form = new ScalaCompilerConfigurationPanel(project)

  private val profiles = form.getProfilesPanel

  def createComponent() = form.getContentPanel

  def isModified = form.getIncrementalityType != configuration.incrementalityType ||
    profiles.getDefaultProfile.getSettings.getState != configuration.defaultProfile.getSettings.getState ||
    !profiles.getModuleProfiles.asScala.corresponds(configuration.customProfiles)(_.getSettings.getState == _.getSettings.getState)

  def reset() {
    form.setIncrementalityType(configuration.incrementalityType)
    profiles.initProfiles(configuration.defaultProfile, configuration.customProfiles.asJava)
  }

  def apply() {
    configuration.incrementalityType = form.getIncrementalityType
    configuration.defaultProfile = profiles.getDefaultProfile
    configuration.customProfiles = profiles.getModuleProfiles.asScala
    DaemonCodeAnalyzer.getInstance(project).restart()
    BuildManager.getInstance().clearState(project)
  }
}

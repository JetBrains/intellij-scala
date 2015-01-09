package org.jetbrains.plugins.scala.project.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.AbstractConfigurable

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaCompilerConfigurable(project: Project, configuration: ScalaCompilerConfiguration) extends AbstractConfigurable("Scala Compiler")  {
  protected val panel = new ScalaCompilerConfigurationPanel(project)

  def createComponent() = panel

  def isModified = panel.getDefaultProfile.getSettings.getState != configuration.defaultProfile.getSettings.getState ||
          !panel.getModuleProfiles.asScala.corresponds(configuration.customProfiles)(_.getSettings.getState == _.getSettings.getState)

  def reset() {
    panel.initProfiles(configuration.defaultProfile, configuration.customProfiles.asJava)
  }

  def apply() {
    configuration.defaultProfile = panel.getDefaultProfile
    configuration.customProfiles = panel.getModuleProfiles.asScala
    DaemonCodeAnalyzer.getInstance(project).restart()
  }
}

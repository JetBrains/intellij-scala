package org.jetbrains.plugins.scala.project.settings

import javax.swing.JPanel

import scala.collection.JavaConverters._

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.Composite
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.hydra.compiler.HydraCompilerConfigurable
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings
import org.jetbrains.plugins.scala.project.AbstractConfigurable

/**
 * @author Pavel Fatin
 */
class ScalaCompilerConfigurable(project: Project, configuration: ScalaCompilerConfiguration) extends AbstractConfigurable("Scala Compiler") with Composite {

  private val form = new ScalaCompilerConfigurationPanel(project)
  
  private val profiles = form.getProfilesPanel

  override def createComponent(): JPanel = form.getContentPanel

  override def isModified: Boolean = form.getIncrementalityType != configuration.incrementalityType ||
          profiles.getDefaultProfile.getSettings.getState != configuration.defaultProfile.getSettings.getState ||
          !profiles.getModuleProfiles.asScala.corresponds(configuration.customProfiles)(_.getSettings.getState == _.getSettings.getState)

  override def reset(): Unit = {
    form.setIncrementalityType(configuration.incrementalityType)
    profiles.initProfiles(configuration.defaultProfile, configuration.customProfiles.asJava)
  }

  override def apply(): Unit = {
    configuration.incrementalityType = form.getIncrementalityType
    configuration.defaultProfile = profiles.getDefaultProfile
    configuration.customProfiles = profiles.getModuleProfiles.asScala
    DaemonCodeAnalyzer.getInstance(project).restart()
    BuildManager.getInstance().clearState(project)
  }

  override def getConfigurables(): Array[Configurable] = {
    if (HydraApplicationSettings.getInstance().isHydraSettingsEnabled)
      Array(new HydraCompilerConfigurable(project))
    else
      Array()
  }
}
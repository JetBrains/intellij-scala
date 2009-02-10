package org.jetbrains.plugins.scala.script

import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType, ConfigurationFactory}
import com.intellij.execution.{RunManager, Location, RunnerAndConfigurationSettings, LocatableConfigurationType}
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.{PsiElement, PsiFile}
import config.ScalaFacet
import console.ScalaScriptConsoleRunConfigurationFactory
import icons.Icons
import javax.swing.Icon
import java.lang.String
import lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.02.2009
 */

class ScalaScriptConfigurationType extends LocatableConfigurationType {
  val confFactory = new ScalaScriptRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCRIPT_FILE_LOGO

  def getDisplayName: String = "Scala Script"

  def getConfigurationTypeDescription: String = "Scala script run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaScriptRunConfiguration"

  def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val file = location.getPsiElement.getContainingFile
    file match {
      case null => return null
      case scalaFile: ScalaFile if scalaFile.isScriptFile => {
        val settings = RunManager.getInstance(location.getProject).createRunConfiguration(scalaFile.getName, confFactory)
        val conf: ScalaScriptRunConfiguration = settings.getConfiguration.asInstanceOf[ScalaScriptRunConfiguration]
        val module = ModuleUtil.findModuleForFile(scalaFile.getVirtualFile, scalaFile.getProject)
        if (module == null || FacetManager.getInstance(module).getFacetByType(ScalaFacet.ID) == null) return null
        conf.setModule(module)
        conf.setScriptPath(scalaFile.getVirtualFile.getPath)
        return settings
      }
      case _ => return null
    }
  }

  def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    configuration match {
      case conf: ScalaScriptRunConfiguration => {
        val file: PsiFile = location.getPsiElement.getContainingFile
        if (file == null || !file.isInstanceOf[ScalaFile]) return false
        return conf.getScriptPath.trim == file.getVirtualFile.getPath.trim
      }
      case _ => return false
    }
  }
}
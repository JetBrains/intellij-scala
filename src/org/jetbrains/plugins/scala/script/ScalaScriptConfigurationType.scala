package org.jetbrains.plugins.scala
package script

import com.intellij.execution.configurations.{ConfigurationType, RunConfiguration, ConfigurationFactory}
import com.intellij.execution.{RunManager, Location, RunnerAndConfigurationSettings}
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.{PsiElement, PsiFile}
import config.ScalaFacet
import icons.Icons
import javax.swing.Icon
import java.lang.String
import lang.psi.api.ScalaFile
import extensions.toPsiNamedElementExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.02.2009
 */

class ScalaScriptConfigurationType extends ConfigurationType {
  val confFactory = new ScalaScriptRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCRIPT_FILE_LOGO

  def getDisplayName: String = "Scala Script"

  def getConfigurationTypeDescription: String = "Scala script run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaScriptRunConfiguration"

  def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val file = location.getPsiElement.getContainingFile
    file match {
      case null => null
      case scalaFile: ScalaFile if (scalaFile.isScriptFile() && !scalaFile.isWorksheetFile) => {
        val settings = RunManager.getInstance(location.getProject).createRunConfiguration(scalaFile.name, confFactory)
        val conf: ScalaScriptRunConfiguration = settings.getConfiguration.asInstanceOf[ScalaScriptRunConfiguration]
        val module = ModuleUtilCore.findModuleForFile(scalaFile.getVirtualFile, scalaFile.getProject)
        if (module == null || !ScalaFacet.isPresentIn(module)) return null
        conf.setModule(module)
        conf.setScriptPath(scalaFile.getVirtualFile.getPath)
        settings
      }
      case _ => null
    }
  }

  def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    configuration match {
      case conf: ScalaScriptRunConfiguration => {
        val file: PsiFile = location.getPsiElement.getContainingFile
        if (file == null || !file.isInstanceOf[ScalaFile]) return false
        conf.getScriptPath.trim == file.getVirtualFile.getPath.trim
      }
      case _ => false
    }
  }
}
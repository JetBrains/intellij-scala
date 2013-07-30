package org.jetbrains.plugins.scala
package worksheet.runconfiguration

import com.intellij.execution.configurations.{ConfigurationType, RunConfiguration, ConfigurationFactory}
import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.execution.{RunManager, RunnerAndConfigurationSettings, Location}
import com.intellij.psi.{PsiFile, PsiElement}
import lang.psi.api.ScalaFile
import com.intellij.openapi.module.ModuleUtilCore
import config.ScalaFacet

/**
 * @author Ksenia.Sautina
 * @since 10/16/12
 */
class WorksheetConfigurationType  extends ConfigurationType {
  val confFactory = new WorksheetRunConfigurationFactory(this)

  //todo icon
  def getIcon: Icon = Icons.SCALA_SMALL_LOGO

  def getDisplayName: String = "Worksheet"

  def getConfigurationTypeDescription: String = "Worksheet run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "WorksheetRunConfiguration"

  def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val file = location.getPsiElement.getContainingFile
    file match {
      case scalaFile: ScalaFile if (scalaFile.isWorksheetFile) => {
        val settings = RunManager.getInstance(location.getProject).createRunConfiguration("WS: " + scalaFile.getName, confFactory)
        val conf: WorksheetRunConfiguration = settings.getConfiguration.asInstanceOf[WorksheetRunConfiguration]
        val module = ModuleUtilCore.findModuleForFile(scalaFile.getVirtualFile, scalaFile.getProject)
        if (module == null || !ScalaFacet.isPresentIn(module)) return null
        conf.setModule(module)
        conf.setWorksheetField(scalaFile.getVirtualFile.getPath)
        settings
      }
      case _ => null
    }
  }

  def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    configuration match {
      case conf: WorksheetRunConfiguration => {
        val file: PsiFile = location.getPsiElement.getContainingFile
        if (file == null || !file.isInstanceOf[ScalaFile]) return false
        conf.getWorksheetField.trim == file.getVirtualFile.getPath.trim
      }
      case _ => false
    }
  }
}

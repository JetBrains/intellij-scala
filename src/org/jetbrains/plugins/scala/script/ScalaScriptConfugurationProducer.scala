package org.jetbrains.plugins.scala
package script

import org.jetbrains.plugins.scala.testingSupport.RuntimeConfigurationProducerAdapter
import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.execution.{RunManager, RunnerAndConfigurationSettings, Location}
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.execution.configurations.RunConfiguration
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import java.util
import project._

/**
 * @author Alefas
 * @since 30.07.13
 */
class ScalaScriptConfugurationProducer extends {
  val configurationType = new ScalaScriptConfigurationType
  val confFactory = configurationType.confFactory
} with RuntimeConfigurationProducerAdapter(configurationType) {
  private var myPsiElement: PsiElement = null
  def getSourceElement: PsiElement = myPsiElement

  override def findExistingByElement(location: Location[_ <: PsiElement], existingConfigurations: util.List[RunnerAndConfigurationSettings],
                                     context: ConfigurationContext): RunnerAndConfigurationSettings = {
    import collection.JavaConversions._
    existingConfigurations.find(c => isConfigurationByLocation(c.getConfiguration, location)).getOrElse(null)
  }

  def createConfigurationByElement(location: Location[_ <: PsiElement], context: ConfigurationContext): RunnerAndConfigurationSettings = {
    myPsiElement = location.getPsiElement
    createConfigurationByLocation(location).asInstanceOf[RunnerAndConfigurationSettingsImpl]
  }

  private def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val file = location.getPsiElement.getContainingFile
    file match {
      case null => null
      case scalaFile: ScalaFile if scalaFile.isScriptFile() && !scalaFile.isWorksheetFile => {
        val settings = RunManager.getInstance(location.getProject).createRunConfiguration(scalaFile.name, confFactory)
        val conf: ScalaScriptRunConfiguration = settings.getConfiguration.asInstanceOf[ScalaScriptRunConfiguration]
        val module = ModuleUtilCore.findModuleForFile(scalaFile.getVirtualFile, scalaFile.getProject)
        if (module == null || !module.hasScala) return null
        conf.setModule(module)
        conf.setScriptPath(scalaFile.getVirtualFile.getPath)
        settings
      }
      case _ => null
    }
  }

  private def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
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

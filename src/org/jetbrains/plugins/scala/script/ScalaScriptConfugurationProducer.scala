package org.jetbrains.plugins.scala
package script

import java.util

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.{Location, RunManager, RunnerAndConfigurationSettings}
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.testingSupport.RuntimeConfigurationProducerAdapter
import org.jetbrains.plugins.scala.project._
import scala.collection.JavaConverters._

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
    existingConfigurations.asScala
      .find(c => isConfigurationByLocation(c.getConfiguration, location))
      .orNull
  }

  def createConfigurationByElement(location: Location[_ <: PsiElement], context: ConfigurationContext): RunnerAndConfigurationSettings = {
    myPsiElement = location.getPsiElement
    createConfigurationByLocation(location).asInstanceOf[RunnerAndConfigurationSettingsImpl]
  }

  private def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val file = location.getPsiElement.getContainingFile
    file match {
      case null => null
      case scalaFile: ScalaFile if scalaFile.isScriptFile && !scalaFile.isWorksheetFile =>
        val settings = RunManager.getInstance(location.getProject).createRunConfiguration(scalaFile.name, confFactory)
        val conf: ScalaScriptRunConfiguration = settings.getConfiguration.asInstanceOf[ScalaScriptRunConfiguration]
        val module = ModuleUtilCore.findModuleForFile(scalaFile.getVirtualFile, scalaFile.getProject)
        if (module == null || !module.hasScala) return null
        conf.setModule(module)
        conf.setScriptPath(scalaFile.getVirtualFile.getPath)
        settings
      case _ => null
    }
  }

  private def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    configuration match {
      case conf: ScalaScriptRunConfiguration =>
        val file: PsiFile = location.getPsiElement.getContainingFile
        if (file == null || !file.isInstanceOf[ScalaFile]) return false
        conf.getScriptPath.trim == file.getVirtualFile.getPath.trim
      case _ => false
    }
  }
}

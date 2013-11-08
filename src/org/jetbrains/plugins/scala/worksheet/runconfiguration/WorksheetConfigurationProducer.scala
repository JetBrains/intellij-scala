package org.jetbrains.plugins.scala
package worksheet.runconfiguration

import com.intellij.execution.junit.RuntimeConfigurationProducer
import com.intellij.execution.{RunManager, RunnerAndConfigurationSettings, Location}
import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.execution.actions.ConfigurationContext
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer
import com.intellij.execution.configurations.RunConfiguration
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.module.ModuleUtilCore
import org.jetbrains.plugins.scala.config.ScalaFacet
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import org.jetbrains.plugins.scala.testingSupport.RuntimeConfigurationProducerAdapter
import java.util

/**
 * @author Alefas
 * @since 30.07.13
 */
class WorksheetConfigurationProducer extends {
  val configurationType = new WorksheetConfigurationType
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

  private def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    configuration match {
      case conf: WorksheetRunConfiguration => {
        val file: PsiFile = location.getPsiElement.getContainingFile
        if (file == null || !file.isInstanceOf[ScalaFile]) return false
        conf.worksheetField.trim == file.getVirtualFile.getPath.trim
      }
      case _ => false
    }
  }

  private def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val file = location.getPsiElement.getContainingFile
    file match {
      case scalaFile: ScalaFile if scalaFile.isWorksheetFile => {
        val settings = RunManager.getInstance(location.getProject).createRunConfiguration("WS: " + scalaFile.getName, confFactory)
        val conf: WorksheetRunConfiguration = settings.getConfiguration.asInstanceOf[WorksheetRunConfiguration]
        val module = ModuleUtilCore.findModuleForFile(scalaFile.getVirtualFile, scalaFile.getProject)
        if (module == null || !ScalaFacet.isPresentIn(module)) return null
        conf.setModule(module)
        conf.worksheetField = scalaFile.getVirtualFile.getPath
        settings
      }
      case _ => null
    }
  }
}

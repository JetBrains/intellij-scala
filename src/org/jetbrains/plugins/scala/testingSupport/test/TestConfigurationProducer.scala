package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.Location
import com.intellij.execution.actions.{ConfigurationContext, RunConfigurationProducer}
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * @author Roman.Shein
 *         Date: 11.12.13
 */
abstract class TestConfigurationProducer(configurationType: ConfigurationType) extends RunConfigurationProducer[AbstractTestRunConfiguration](configurationType) with AbstractTestConfigurationProducer{

  protected def isObjectInheritor(clazz: ScTypeDefinition, fqn: String): Boolean =
    ScalaPsiManager.instance(clazz.getProject)
      .getCachedClass(fqn, clazz.getResolveScope, ScalaPsiManager.ClassCategory.OBJECT).exists {
      ScalaPsiUtil.cachedDeepIsInheritor(clazz, _)
    }

  def getLocationClassAndTest(location: Location[_ <: PsiElement]): (ScTypeDefinition, String)

  override def setupConfigurationFromContext(configuration: AbstractTestRunConfiguration, context: ConfigurationContext, sourceElement: Ref[PsiElement]): Boolean = {
    if (sourceElement.isNull) {
      false
    }
    else {
      createConfigurationByElement(context.getLocation, context) match {
        case Some((testElement, resConfig)) if testElement != null && resConfig != null =>
          sourceElement.set(testElement)
          val cfg = resConfig.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
          configuration.setTestClassPath(cfg.getTestClassPath)
          configuration.setGeneratedName(cfg.suggestedName)
          configuration.setJavaOptions(cfg.getJavaOptions)
          configuration.setTestArgs(cfg.getTestArgs)
          configuration.setTestPackagePath(cfg.getTestPackagePath)
          configuration.setWorkingDirectory(cfg.getWorkingDirectory)
          configuration.setTestName(cfg.getTestName)
          configuration.setSearchTest(cfg.getSearchTest)
          configuration.setShowProgressMessages(cfg.getShowProgressMessages)
          configuration.setFileOutputPath(cfg.getOutputFilePath)
          configuration.setModule(cfg.getModule)
          configuration.setName(cfg.getName)
          configuration.setNameChangedByUser(!cfg.isGeneratedName)
          configuration.setSaveOutputToFile(cfg.isSaveOutputToFile)
          configuration.setShowConsoleOnStdErr(cfg.isShowConsoleOnStdErr)
          configuration.setShowConsoleOnStdOut(cfg.isShowConsoleOnStdOut)
          configuration.setTestKind(cfg.getTestKind)
          true
        case _ =>
          false
      }
    }
  }

  override def isConfigurationFromContext(configuration: AbstractTestRunConfiguration, context: ConfigurationContext): Boolean = {
    //TODO: implement me properly
    val runnerClassName = configuration.mainClass

    if (runnerClassName != null && runnerClassName == configuration.mainClass) {
      val configurationModule: Module = configuration.getConfigurationModule.getModule
      if (context.getLocation != null) {
        isConfigurationByLocation(configuration, context.getLocation)
      } else {
        (context.getModule == configurationModule ||
                context.getRunManager.getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
                        .getConfigurationModule.getModule == configurationModule) && configuration.getTestClassPath == null && configuration.getTestName == null
      }
    } else false
  }
}

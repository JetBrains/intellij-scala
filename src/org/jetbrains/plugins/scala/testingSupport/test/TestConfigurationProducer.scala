package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.actions.{ConfigurationContext, RunConfigurationProducer}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, AbstractTestRunConfiguration}
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.{RunnerAndConfigurationSettings, Location}
import com.intellij.psi.PsiElement
import com.intellij.openapi.util.Ref
import com.intellij.openapi.module.Module

/**
 * @author Roman.Shein
 *         Date: 11.12.13
 */
abstract class TestConfigurationProducer(configurationType: ConfigurationType) extends RunConfigurationProducer[AbstractTestRunConfiguration](configurationType) with AbstractTestConfigurationProducer{

  def getLocationClassAndTest(location: Location[_ <: PsiElement]): (String, String)

  override def setupConfigurationFromContext(configuration: AbstractTestRunConfiguration, context: ConfigurationContext, sourceElement: Ref[PsiElement]): Boolean = {
    if (sourceElement.isNull) {
      false
    }
    else {
      val resConfig: RunnerAndConfigurationSettings = createConfigurationByElement(context.getLocation, context)
      if (resConfig != null) {
        //TODO: move field assignment into configuration creation
        val cfg = resConfig.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
        configuration.setTestClassPath(cfg.getTestClassPath)
        configuration.setGeneratedName(cfg.getGeneratedName)
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
      }
      else false
    }
  }

  override def isConfigurationFromContext(configuration: AbstractTestRunConfiguration, context: ConfigurationContext): Boolean = {
    //TODO: implement me properly
    val runnerClassName = configuration.mainClass

    if (runnerClassName != null && runnerClassName == configuration.mainClass) {
      val configurationModule: Module = configuration.getConfigurationModule.getModule
      val testNameOk = if (context.getLocation != null) {
        val (testClass, testName) = getLocationClassAndTest(context.getLocation)
        testClass == configuration.getTestClassPath &&
          (testName == null && configuration.getTestName == "" || testName == configuration.getTestName)
      } else {
        configuration.getTestClassPath == null && configuration.getTestName == null
      }
      (context.getModule == configurationModule ||
        context.getRunManager.getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
          .getConfigurationModule.getModule == configurationModule) && testNameOk
    } else false
  }
}

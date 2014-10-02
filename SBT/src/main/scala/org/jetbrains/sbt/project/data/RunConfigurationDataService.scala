package org.jetbrains.sbt.project.data

import java.util

import com.intellij.execution.RunManager
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.configurations.{ConfigurationFactory, RunConfiguration}
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind
import org.jetbrains.sbt.project.structure.{SbtScalaTestRunConfiguration, SbtAppRunConfiguration}

import scala.collection.JavaConverters._
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.{ScalaTestRunConfiguration, ScalaTestConfigurationType}

class RunConfigurationDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[RunConfigurationData, RunConfiguration](RunConfigurationData.Key) {
  override def doImportData(toImport: util.Collection[DataNode[RunConfigurationData]], project: Project): Unit = {
    toImport.asScala.foreach { node =>
      val data = node.getData.configuration
      val runManager: RunManager = RunManager.getInstance(project)
      data match {
        case SbtAppRunConfiguration(mainClass, homePath, vmOpts, moduleName, artifacts) =>
          val factories: Array[ConfigurationFactory] = ApplicationConfigurationType.getInstance().getConfigurationFactories
          val settings = runManager.createRunConfiguration(s"SBT-${data.moduleName}", factories.head)
          val configuration: ApplicationConfiguration = settings.getConfiguration.asInstanceOf[ApplicationConfiguration]
          configuration.setMainClassName(mainClass)
          configuration.setWorkingDirectory(homePath)
          configuration.setModule(ModuleManager.getInstance(project).findModuleByName(moduleName))
          configuration.VM_PARAMETERS = data.vmOpts.mkString(" ")
          runManager.addConfiguration(settings, true)
        case SbtScalaTestRunConfiguration(inPackage, homePath, vmOptions, moduleName, artifacts) => {
          val factories = new ScalaTestConfigurationType().getConfigurationFactories
          val settings = runManager.createRunConfiguration(s"ScalaTest-${data.moduleName}", factories.head)
          val configuration = settings.getConfiguration.asInstanceOf[ScalaTestRunConfiguration]
          configuration.setTestKind(TestKind.ALL_IN_PACKAGE)
          configuration.setTestPackagePath(inPackage)
          configuration.setWorkingDirectory(homePath)
          configuration.setModule(ModuleManager.getInstance(project).findModuleByName(moduleName))
          configuration.setJavaOptions(vmOptions.mkString(" "))
          runManager.addConfiguration(settings, true)
        }
      }
    }
  }

  override def doRemoveData(toRemove: util.Collection[_ <: RunConfiguration], project: Project) = ()
}

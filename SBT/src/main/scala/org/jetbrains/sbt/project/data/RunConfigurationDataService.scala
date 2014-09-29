package org.jetbrains.sbt.project.data

import java.util

import com.intellij.execution.RunManager
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.configurations.{ConfigurationFactory, RunConfiguration}
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

import scala.collection.JavaConverters._

class RunConfigurationDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[RunConfigurationData, RunConfiguration](RunConfigurationData.Key) {
  override def doImportData(toImport: util.Collection[DataNode[RunConfigurationData]], project: Project): Unit = {
    toImport.asScala.foreach { node =>
      val data: RunConfigurationData = node.getData
      val runManager: RunManager = RunManager.getInstance(project)
      val factories: Array[ConfigurationFactory] = ApplicationConfigurationType.getInstance().getConfigurationFactories
      val settings = runManager.createRunConfiguration(s"SBT-${data.moduleName}", factories.head)
      val configuration: ApplicationConfiguration = settings.getConfiguration.asInstanceOf[ApplicationConfiguration]
      configuration.setMainClassName(data.mainClass)
      configuration.setWorkingDirectory(data.homePath)
      configuration.setModule(ModuleManager.getInstance(project).findModuleByName(data.moduleName))
//      RunManager.getInstance(project).
      configuration.VM_PARAMETERS = data.vmOpts.mkString(" ")
      runManager.addConfiguration(settings, true)
    }
  }

  override def doRemoveData(toRemove: util.Collection[_ <: RunConfiguration], project: Project): Unit = {

    ???
  }
}

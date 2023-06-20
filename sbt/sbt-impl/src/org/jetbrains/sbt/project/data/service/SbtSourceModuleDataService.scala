package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.util.SmartList
import org.jetbrains.sbt.SbtModuleData
import org.jetbrains.sbt.project.module.SourceModule

import java.util
import scala.util.Try
import scala.jdk.CollectionConverters.CollectionHasAsScala

class SbtSourceModuleDataService extends AbstractModuleDataService[ModuleData] {

//  override def computeOrphanData(toImport: util.Collection[_ <: DataNode[ModuleData]], projectData: ProjectData, project: Project, modelsProvider: IdeModifiableModelsProvider): Computable[util.Collection[Module]] = {
//    () => {
//      val orphanIdeModules = new SmartList[Module]
//      modelsProvider.getModules
//        .filterNot(_.isDisposed)
//        .filter(ExternalSystemApiUtil.isExternalSystemAwareModule(projectData.getOwner, _))
//        .filter(ExternalSystemApiUtil.getExternalModuleType(_) == SourceModule.externalModuleType)
//        .foreach { module =>
//          val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
//          if (projectData.getLinkedExternalProjectPath == rootProjectPath && module.getUserData(AbstractModuleDataService.MODULE_DATA_KEY) == null)
//            orphanIdeModules.add(module)
//        }
//      orphanIdeModules
//    }
//  }


  override def setModuleOptions(module: Module, moduleDataNode: DataNode[ModuleData]): Unit = {
    super.setModuleOptions(module, moduleDataNode)
    val sbtModuleData = moduleDataNode.getChildren.asScala.toSeq.flatMap(node => Try(node.getData.asInstanceOf[SbtModuleData]).toOption)
//    sbtModuleData.filter(_.isSourceModule).foreach { _ =>
//      ExternalSystemModulePropertyManager.getInstance(module).setExternalModuleType(SourceModule.externalModuleType)
//    }
  }
  override def getTargetDataKey: Key[ModuleData] = ProjectKeys.MODULE
}
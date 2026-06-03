package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import org.jetbrains.sbt.project.data.findModuleForParentOfDataNode

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class AbstractSbtModuleDataService[T <: ModuleData] extends AbstractModuleDataService[T] {

  override def setModuleOptions(module: Module, moduleDataNode: DataNode[T]): Unit = {
    super.setModuleOptions(module, moduleDataNode)
    // TODO: Override #getExternalModuleType so the platform sets the external module type in #setModuleOptions,
    //  making this method unnecessary.
    ExternalSystemModulePropertyManager.getInstance(module).setExternalModuleType(moduleType)
  }

  override def importData(
    toImport: util.Collection[? <: DataNode[T]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    toImport.asScala.foreach(updateModuleNameWithParentPrefix(_, modelsProvider))
    super.importData(toImport, projectData, project, modelsProvider)
  }

  override def computeOrphanData(
    toImport: util.Collection[? <: DataNode[T]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Computable[util.Collection[Module]] = {
    () => {
      val orphanIdeModules = new java.util.ArrayList[Module]()

      modelsProvider.getModules.foreach { module =>
        val isPossibleOrphan = !module.isDisposed && ExternalSystemApiUtil.isExternalSystemAwareModule(projectData.getOwner, module) &&
          ExternalSystemApiUtil.getExternalModuleType(module) == moduleType
        if (isPossibleOrphan) {
          val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
          val isModuleKeyNull = module.getUserData(AbstractModuleDataService.MODULE_DATA_KEY) == null
          if (projectData.getLinkedExternalProjectPath.equals(rootProjectPath) && isModuleKeyNull) {
            orphanIdeModules.add(module)
          }
        }
      }
      orphanIdeModules
    }
  }

  override def createModule(
    module: DataNode[T],
    modelsProvider: IdeModifiableModelsProvider
  ): Module = {
    updateModuleNameWithParentPrefix(module, modelsProvider)
    super.createModule(module, modelsProvider)
  }

  protected def moduleType: String

  protected def generateNewName(
    parentModule: Module,
    data: T,
    parentModuleActualName: String,
  ): Option[String]

  private def updateModuleNameWithParentPrefix(dataNode: DataNode[T], modelsProvider: IdeModifiableModelsProvider): Unit = {
    val parentModuleOpt = findModuleForParentOfDataNode(dataNode)
    val data = dataNode.getData
    val newInternalModuleName = parentModuleOpt.flatMap { parentModule =>
      val parentModuleActualName = modelsProvider.getModifiableModuleModel.getActualName(parentModule)
      val internalModuleName = data.getInternalName
      if (!internalModuleName.startsWith(parentModuleActualName)) {
        generateNewName(parentModule, data, parentModuleActualName)
      } else {
        None
      }
    }
    newInternalModuleName.foreach(data.setInternalName)
  }
}

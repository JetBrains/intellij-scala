package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.data.findModuleForParentOfDataNode

import java.util

abstract class AbstractSbtModuleDataService[T <: ModuleData] extends AbstractModuleDataService[T] {

  override def computeOrphanData(
    toImport: util.Collection[_ <: DataNode[T]],
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
          if (projectData.getLinkedExternalProjectPath.equals(rootProjectPath)) {
            if (module.getUserData(AbstractModuleDataService.MODULE_DATA_KEY) == null) {
              orphanIdeModules.add(module)
            }
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
    val newInternalModuleName = generateNewInternalModuleNameIfApplicable(module, modelsProvider)

    newInternalModuleName.foreach { moduleName =>
      val adjustedInternalName = findDeduplicatedModuleName(moduleName, modelsProvider)
      module.getData.setInternalName(adjustedInternalName)
    }
    super.createModule(module, modelsProvider)
  }

  protected def moduleType: String

  protected def generateNewName(
    parentModule: Module,
    data: T,
    parentModuleActualName: String,
  ): Option[String]

  private def generateNewInternalModuleNameIfApplicable(
    dataNode: DataNode[T],
    modelsProvider: IdeModifiableModelsProvider
  ): Option[String] = {
    val parentModuleOpt = findModuleForParentOfDataNode(dataNode)
    parentModuleOpt.flatMap { parentModule =>
      val data = dataNode.getData
      val parentModuleActualName = modelsProvider.getModifiableModuleModel.getActualName(parentModule)
      val internalModuleName = data.getInternalName
      if (!internalModuleName.startsWith(parentModuleActualName)) {
        generateNewName(parentModule, data, parentModuleActualName)
      } else {
        // note: returning Option with the same name is important for #createModule method - when even the same module name is returned,
        // it is used to find the deduplicated module name, and if it is necessary add "~" suffix to make it unique.
        Some(internalModuleName)
      }
    }
  }

  /**
   * When new module is created, it must be ensured that IDEA will not be able to find an existing module with that name.
   * Otherwise IDEA will prepend module name with parent directories names and the structure of the modules will be disturbed.
   * The described situation happens in
   * [[com.intellij.openapi.externalSystem.service.project.AbstractIdeModifiableModelsProvider#newModule(com.intellij.openapi.externalSystem.model.project.ModuleData)]] .<br>
   * If the module name provided in the parameter is already being used by another module, a suffix consisting of a tilde and a number is appended to the module name (e.g. <code>~1</code>).
   */
  private def findDeduplicatedModuleName(
    moduleName: String,
    modelsProvider: IdeModifiableModelsProvider,
  ): String = {
    var moduleNameCandidate = moduleName
    var inc = 1
    var isUnique = false
    while(!isUnique) {
      val ideModule = modelsProvider.findIdeModule(moduleNameCandidate)
      if (ideModule == null) {
        isUnique = true
      } else {
        moduleNameCandidate = SbtUtil.appendSuffixToModuleName(moduleName, inc)
        inc += 1
      }
    }
    moduleNameCandidate
  }
}

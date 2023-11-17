package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.{getExternalProjectPath, isExternalSystemAwareModule}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.scala.util.ExternalSystemUtil
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.module.SbtNestedModuleData

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala


class SbtNestedModuleDataService extends AbstractModuleDataService[SbtNestedModuleData]{

  override def getTargetDataKey: Key[SbtNestedModuleData] = Sbt.sbtNestedModuleDataKey

  override def computeOrphanData(
    toImport: util.Collection[_ <: DataNode[SbtNestedModuleData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Computable[util.Collection[Module]] = {
    () => {
      val orphanIdeModules = new java.util.ArrayList[Module]()

      modelsProvider.getModules.foreach { module =>
        val isPossibleOrphan = !(module.isDisposed || !ExternalSystemApiUtil.isExternalSystemAwareModule(projectData.getOwner, module) ||
          ExternalSystemApiUtil.getExternalModuleType(module) != Sbt.SbtNestedModuleTypeKey)
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


  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtNestedModuleData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    toImport.asScala.foreach { sbtNestedModuleNode =>
      val newInternalModuleName = generateNewInternalModuleNameIfApplicable(sbtNestedModuleNode, modelsProvider)
      newInternalModuleName match {
        case Some(name) if isModuleNameApplicableToModuleData(name, modelsProvider, sbtNestedModuleNode.getData) =>
          sbtNestedModuleNode.getData.setInternalName(name)
        case _ =>
      }
    }
    super.importData(toImport, projectData, project, modelsProvider)
  }

  override def createModule(
    module: DataNode[SbtNestedModuleData],
    modelsProvider: IdeModifiableModelsProvider
  ): Module = {
    val newInternalModuleName = generateNewInternalModuleNameIfApplicable(module, modelsProvider)

    newInternalModuleName.foreach { moduleName =>
      val adjustedInternalName = findDeduplicatedModuleName(moduleName, modelsProvider)
      module.getData.setInternalName(adjustedInternalName)
    }
    super.createModule(module, modelsProvider)
  }

  override def setModuleOptions(module: Module, moduleDataNode: DataNode[SbtNestedModuleData]): Unit = {
    super.setModuleOptions(module, moduleDataNode)
    ExternalSystemModulePropertyManager.getInstance(module).setExternalModuleType(Sbt.SbtNestedModuleTypeKey)
  }

  private def generateNewInternalModuleNameIfApplicable(
    dataNode: DataNode[SbtNestedModuleData],
    modelsProvider: IdeModifiableModelsProvider
  ): Option[String] = {
    val sbtNestedModuleData = dataNode.getData
    val parentModuleOpt = Option(dataNode.getParent).flatMap { parent =>
      Option(parent.getUserData(AbstractModuleDataService.MODULE_KEY))
    }

    parentModuleOpt.flatMap { parentModule =>
      val parentModuleActualName = modelsProvider.getModifiableModuleModel.getActualName(parentModule)
      val internalModuleName = sbtNestedModuleData.getInternalName
      if (!internalModuleName.startsWith(parentModuleActualName)) {
        val moduleName = sbtNestedModuleData.getModuleName
        val parentModuleOriginalName = findParentModuleOriginalName(parentModule)
        parentModuleOriginalName
          .map(generateNewInternalModuleName(internalModuleName, moduleName, _, parentModuleActualName))
      } else {
        None
      }
    }
  }

  private def findParentModuleOriginalName(module: Module): Option[String] = {
    val moduleId = Option(ExternalSystemApiUtil.getExternalProjectId(module))
    moduleId match {
      case Some(id) =>
        val project = module.getProject
        val moduleData = ExternalSystemUtil.getModuleDataNode(SbtProjectSystem.Id, project, id, None)
        moduleData.map(_.getData.getModuleName)
      case _ => None
    }
  }

  private def generateNewInternalModuleName(
    internalModuleName: String,
    moduleName: String,
    parentOriginalModuleName: String,
    parentActualModuleName: String
  ): String = {
      val groupNameInsideBuild = internalModuleName.stripPrefix(s"$parentOriginalModuleName.").stripSuffix(moduleName)
      s"$parentActualModuleName.$groupNameInsideBuild$moduleName"
  }

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
        moduleNameCandidate = moduleName + "~" + inc
        inc += 1
      }
    }
    moduleNameCandidate
  }

  private def isModuleNameApplicableToModuleData(
    moduleName: String,
    modelsProvider: IdeModifiableModelsProvider,
    moduleData: SbtNestedModuleData
  ): Boolean = {
    val ideModule = modelsProvider.findIdeModule(moduleName)
    Option(ideModule).exists { module =>
      //note: in general this logic was taken from com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl.isApplicableIdeModule
      for (root <- ModuleRootManager.getInstance(module).getContentRoots) {
        if (VfsUtilCore.pathEqualsTo(root, moduleData.getLinkedExternalProjectPath)) return true
      }
      isExternalSystemAwareModule(moduleData.getOwner, module) && pathsEqual(getExternalProjectPath(module), moduleData.getLinkedExternalProjectPath)
    }
  }
}

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
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.scala.util.ExternalSystemUtil
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.{computeOrphanDataForModuleType, findModuleForParentOfDataNode}
import org.jetbrains.sbt.project.module.SbtNestedModuleData
import org.jetbrains.sbt.settings.SbtSettings

import java.util
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SetHasAsJava}

class SbtNestedModuleDataService extends AbstractModuleDataService[SbtNestedModuleData]{

  import org.jetbrains.sbt.project.data.service.SbtNestedModuleDataService.sbtNestedModuleType

  override def getTargetDataKey: Key[SbtNestedModuleData] = SbtNestedModuleData.Key

  override def computeOrphanData(
    toImport: util.Collection[_ <: DataNode[SbtNestedModuleData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Computable[util.Collection[Module]] =
    computeOrphanDataForModuleType(sbtNestedModuleType, projectData, modelsProvider)

  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtNestedModuleData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    setModulesInExternalSystemSettings(project, toImport.asScala.toList)
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
    ExternalSystemModulePropertyManager.getInstance(module).setExternalModuleType(sbtNestedModuleType)
  }

  private def generateNewInternalModuleNameIfApplicable(
    dataNode: DataNode[SbtNestedModuleData],
    modelsProvider: IdeModifiableModelsProvider
  ): Option[String] = {
    val parentModuleOpt = findModuleForParentOfDataNode(dataNode)

    parentModuleOpt.flatMap { parentModule =>
      val parentModuleActualName = modelsProvider.getModifiableModuleModel.getActualName(parentModule)
      val sbtNestedModuleData = dataNode.getData
      val internalModuleName = sbtNestedModuleData.getInternalName
      if (!internalModuleName.startsWith(parentModuleActualName)) {
        val moduleName = sbtNestedModuleData.getModuleName
        val parentModuleOriginalName = findParentModuleOriginalName(parentModule)
        parentModuleOriginalName
          .map(generateNewInternalModuleName(internalModuleName, moduleName, _, parentModuleActualName))
      } else {
        // note: returning Option with the same name is important for #createModule method - when even the same module name is returned,
        // it is used to find the deduplicated module name, and if it is necessary add "~" suffix to make it unique.
        Some(internalModuleName)
      }
    }
  }

  private def findParentModuleOriginalName(module: Module): Option[String] = {
    val moduleId = Option(ExternalSystemApiUtil.getExternalProjectId(module))
    moduleId.flatMap { id =>
      val project = module.getProject
      val rootProjectPath = Option(ExternalSystemApiUtil.getExternalRootProjectPath(module))
      val moduleData = ExternalSystemUtil.getModuleDataNode(SbtProjectSystem.Id, project, id, rootProjectPath, None)
      moduleData.map(_.getData.getModuleName)
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

  private def isModuleNameApplicableToModuleData(
    moduleName: String,
    modelsProvider: IdeModifiableModelsProvider,
    moduleData: SbtNestedModuleData
  ): Boolean = {
    val ideModule = modelsProvider.findIdeModule(moduleName)
    ideModule != null && {
      //note: the logic is copied from the private method com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl.isApplicableIdeModule
      for (root <- ModuleRootManager.getInstance(ideModule).getContentRoots) {
        if (VfsUtilCore.pathEqualsTo(root, moduleData.getLinkedExternalProjectPath)) return true
      }
      isExternalSystemAwareModule(moduleData.getOwner, ideModule) && pathsEqual(getExternalProjectPath(ideModule), moduleData.getLinkedExternalProjectPath)
    }
  }

  //note: before introducing SbtNestedModuleData setting modules in com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask.doExecute was enough, but because
  // the logic there does not take into account modules with keys different than ProjectKeys.MODULE it was needed to implement it on our own for SbtNestedModuleData.
  // It is needed for com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl.suggestModuleNameCandidates method to choose the proper delimiter for ModuleNameGenerator.
  private def setModulesInExternalSystemSettings(project: Project, sbtNestedModules: List[_ <: DataNode[SbtNestedModuleData]]): Unit = {
    val linkedProjectSettings = SbtSettings.getInstance(project).getLinkedProjectSettings(project.getBasePath)
    if (linkedProjectSettings != null) {
      val sbtNestedModulePaths = sbtNestedModules.map(_.getData).map(_.externalConfigPath)

      val externalModulePaths = (sbtNestedModulePaths ++ linkedProjectSettings.getModules.asScala.toSeq).toSet
      linkedProjectSettings.setModules(externalModulePaths.asJava)
    }
  }
}

object SbtNestedModuleDataService {
  @VisibleForTesting
  val sbtNestedModuleType = "nestedProject"
}

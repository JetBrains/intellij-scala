package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.{getExternalProjectPath, isExternalSystemAwareModule}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemConstants, Order}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.scala.util.ExternalSystemUtil
import org.jetbrains.sbt.Sbt.SbtModuleChildKeyInstance
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.module.{SbtNestedModuleData, SbtSourceSetData}
import org.jetbrains.sbt.project.data.findModuleForParentOfDataNode
import org.jetbrains.sbt.settings.SbtSettings

import java.util
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SetHasAsJava}

class SbtNestedModuleDataService extends AbstractSbtModuleDataService[SbtNestedModuleData]{

  import org.jetbrains.sbt.project.data.service.SbtNestedModuleDataService.sbtNestedModuleType

  override def getTargetDataKey: Key[SbtNestedModuleData] = SbtNestedModuleData.Key

  override protected def moduleType: String = sbtNestedModuleType

//  override def importData(
//    toImport: util.Collection[_ <: DataNode[SbtNestedModuleData]],
//    projectData: ProjectData,
//    project: Project,
//    modelsProvider: IdeModifiableModelsProvider
//  ): Unit = {
//    setModulesInExternalSystemSettings(project, toImport.asScala.toList)
//    toImport.asScala.foreach { sbtNestedModuleNode =>
//      val newInternalModuleName = generateNewInternalModuleNameIfApplicable(sbtNestedModuleNode, modelsProvider)
//      newInternalModuleName match {
//        case Some(name) if isModuleNameApplicableToModuleData(name, modelsProvider, sbtNestedModuleNode.getData) =>
//          sbtNestedModuleNode.getData.setInternalName(name)
//        case _ =>
//      }
//    }
//    super.importData(toImport, projectData, project, modelsProvider)
//  }

  override def setModuleOptions(module: Module, moduleDataNode: DataNode[SbtNestedModuleData]): Unit = {
    super.setModuleOptions(module, moduleDataNode)
    ExternalSystemModulePropertyManager.getInstance(module).setExternalModuleType(sbtNestedModuleType)
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

  override protected def generateNewName(
    parentModule: Module,
    data: SbtNestedModuleData,
    parentModuleActualName: String
  ): Option[String] = {
    val moduleName = data.getModuleName
    val parentModuleOriginalName = findParentModuleOriginalName(parentModule)
    parentModuleOriginalName
      .map(generateNewInternalModuleName(data.getInternalName, moduleName, _, parentModuleActualName))
  }

  private def findParentModuleOriginalName(module: Module): Option[String] = {
    val moduleId = Option(ExternalSystemApiUtil.getExternalProjectId(module))
    moduleId.flatMap { id =>
      val project = module.getProject
      val rootProjectPath = Option(ExternalSystemApiUtil.getExternalRootProjectPath(module))
      val moduleData = ExternalSystemUtil.getModuleDataNode(SbtProjectSystem.Id, project, id, rootProjectPath, Some(SbtModuleChildKeyInstance))
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
}

object SbtNestedModuleDataService {
  @VisibleForTesting
  val sbtNestedModuleType = "nestedProject"
}

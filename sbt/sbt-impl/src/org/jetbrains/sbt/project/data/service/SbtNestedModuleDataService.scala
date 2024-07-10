package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.module.SbtNestedModuleData
import org.jetbrains.sbt.settings.SbtSettings

import java.util
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SetHasAsJava}

class SbtNestedModuleDataService extends AbstractSbtModuleDataService[SbtNestedModuleData]{

  import org.jetbrains.sbt.project.data.service.SbtNestedModuleDataService.sbtNestedModuleType

  override def getTargetDataKey: Key[SbtNestedModuleData] = SbtNestedModuleData.Key

  override protected def moduleType: String = sbtNestedModuleType

  override def setModuleOptions(module: Module, moduleDataNode: DataNode[SbtNestedModuleData]): Unit = {
    super.setModuleOptions(module, moduleDataNode)
    ExternalSystemModulePropertyManager.getInstance(module).setExternalModuleType(sbtNestedModuleType)
  }

  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtNestedModuleData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    setModulesInExternalSystemSettings(project, projectData.getLinkedExternalProjectPath, toImport.asScala.toList)
    super.importData(toImport, projectData, project, modelsProvider)
  }

  override protected def generateNewName(
    parentModule: Module,
    data: SbtNestedModuleData,
    parentModuleActualName: String
  ): Option[String] = {
    val parentModuleOriginalName = findParentModuleOriginalName(parentModule)
    parentModuleOriginalName
      .map(generateNewInternalModuleName(data.getInternalName, data.getModuleName, _, parentModuleActualName))
  }

  //note: before introducing SbtNestedModuleData setting modules in com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask.doExecute was enough, but because
  // the logic there does not take into account modules with keys different than ProjectKeys.MODULE it was needed to implement it on our own for SbtNestedModuleData.
  // It is needed for com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl.suggestModuleNameCandidates method to choose the proper delimiter for ModuleNameGenerator.
  private def setModulesInExternalSystemSettings(
    project: Project,
    rootProjectPath: String,
    sbtNestedModules: List[_ <: DataNode[SbtNestedModuleData]]
  ): Unit = {
    val linkedProjectSettings = SbtSettings.getInstance(project).getLinkedProjectSettings(rootProjectPath)
    if (linkedProjectSettings != null) {
      val sbtNestedModulePaths = sbtNestedModules.map(_.getData).map(_.externalConfigPath)

      val externalModulePaths = (sbtNestedModulePaths ++ linkedProjectSettings.getModules.asScala.toSeq).toSet
      linkedProjectSettings.setModules(externalModulePaths.asJava)
    }
  }

  private def findParentModuleOriginalName(module: Module): Option[String] = {
    val moduleDataNode = SbtUtil.getSbtModuleDataNode(module)
    moduleDataNode.map(_.getData.getModuleName)
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

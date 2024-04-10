package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService
import org.jetbrains.sbt.RichSeq
import org.jetbrains.sbt.project.SharedSourcesOwnersData
import org.jetbrains.sbt.project.data.findModuleForParentOfDataNode
import org.jetbrains.sbt.project.settings.SharedSourcesOwnerModules

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

// TODO SCL-22395
class SharedSourcesOwnersDataService extends ScalaAbstractProjectDataService[SharedSourcesOwnersData, Module](SharedSourcesOwnersData.Key){

  override def importData(
    toImport: util.Collection[_ <: DataNode[SharedSourcesOwnersData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    val projectIdToModuleName = getProjectIdToActualModuleNameMap(modelsProvider)
    toImport.asScala.foreach { sharedSourcesOwnersNode =>
      val ownersModuleNames = sharedSourcesOwnersNode.getData.ownerModuleIds
      val actualOwnerModuleNames = ownersModuleNames.asScala.map(projectIdToModuleName).toSeq
      val parentModule = findModuleForParentOfDataNode(sharedSourcesOwnersNode)
      parentModule.foreach(updateSharedSourcesOwnerModules(_, actualOwnerModuleNames))
    }
    super.importData(toImport, projectData, project, modelsProvider)
  }

  private def updateSharedSourcesOwnerModules(module: Module, ownerModuleNames: Seq[String]): Unit = {
    val sharedSourcesOwnerModules = SharedSourcesOwnerModules.getInstance(module)
    sharedSourcesOwnerModules.getState.ownersModuleNames = ownerModuleNames.toJavaList
  }

  private def getProjectIdToActualModuleNameMap(modelsProvider: IdeModifiableModelsProvider): Map[String, String] =
    modelsProvider.getModules.flatMap { module =>
      val externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module)
      if (externalProjectId == null) None
      else {
        val actualModuleName = modelsProvider.getModifiableModuleModel.getActualName(module)
        Some(externalProjectId -> actualModuleName)
      }
    }.toMap
}

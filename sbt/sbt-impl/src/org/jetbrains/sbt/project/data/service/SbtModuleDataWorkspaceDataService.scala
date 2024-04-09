package org.jetbrains.sbt.project.data.service

import com.intellij.entities.{ModuleExtensionWorkspaceEntityKt, SbtEntitySource, SbtModuleEntity}
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.service.project.manage.WorkspaceDataService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.{ModuleEntityAndExtensions, ModuleId}
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.sbt.SbtUtil.EntityStorageOps
import org.jetbrains.sbt.WorkspaceModelUtil
import org.jetbrains.sbt.project.data.{SbtModuleData, findModuleForParentOfDataNode}

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

class SbtModuleDataWorkspaceDataService extends WorkspaceDataService[SbtModuleData] {

  override def getTargetDataKey: Key[SbtModuleData] = SbtModuleData.Key

  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtModuleData]],
    projectData: ProjectData,
    project: Project,
    mutableStorage: MutableEntityStorage
  ): Unit = {
    toImport.asScala.foreach { dataNode =>
      val moduleOpt = findModuleForParentOfDataNode(dataNode)

      moduleOpt.foreach { module =>
        val moduleEntityOpt = mutableStorage.resolveOpt(new ModuleId(module.getName))
        moduleEntityOpt
          // note: checking whether SbtModuleEntity already exists for ModuleEntity and
          // create a new one only if it does not exist
          .filter(WorkspaceModelUtil.findSbtModuleEntityForModuleEntity(_, mutableStorage).isEmpty)
          .foreach { moduleEntity =>
            val sbtModuleData = dataNode.getData
            val newEntity = createSbtModuleEntity(sbtModuleData, project)
            ModuleEntityAndExtensions.modifyEntity(mutableStorage, moduleEntity, builder => {
              ModuleExtensionWorkspaceEntityKt.setModuleExtensionWorkspaceEntity(builder, newEntity)
              kotlin.Unit.INSTANCE
            })
          }
      }
    }
  }

  private def createSbtModuleEntity(sbtModuleData: SbtModuleData, project: Project): SbtModuleEntity.Builder = {
    val vfUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager

    val sbtModuleDataUri = sbtModuleData.buildURI.toString
    val buildSbtVirtualFileUrl = vfUrlManager.getOrCreateFromUrl(sbtModuleDataUri + "build.sbt")
    val entitySource = new SbtEntitySource(buildSbtVirtualFileUrl)

    val baseDirectoryVirtualFileUrl = vfUrlManager.fromPath(sbtModuleData.baseDirectory.toString)

    WorkspaceEntitiesCompanionProxy.SbtModuleEntityCompanion.create(sbtModuleData.id, sbtModuleDataUri, baseDirectoryVirtualFileUrl, entitySource)
  }
}


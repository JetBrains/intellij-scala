package org.jetbrains.sbt.project.data.service

import com.intellij.entities.{ModuleExtensionWorkspaceEntityKt, SharedSourcesOwnersEntity}
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.service.project.manage.WorkspaceDataService
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.{ModuleEntityAndExtensions, ModuleId}
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.sbt.SbtUtil.EntityStorageOps
import org.jetbrains.sbt.WorkspaceModelUtil
import org.jetbrains.sbt.project.SharedSourcesOwnersData
import org.jetbrains.sbt.project.data.findModuleForParentOfDataNode

import java.util
import java.util.{List => JList}
import scala.jdk.CollectionConverters.CollectionHasAsScala

// TODO SCL-22395
class SharedSourcesOwnersDataWorkspaceDataService extends WorkspaceDataService[SharedSourcesOwnersData] {

  override def getTargetDataKey: Key[SharedSourcesOwnersData] = SharedSourcesOwnersData.Key

  override def importData(
    toImport: util.Collection[_ <: DataNode[SharedSourcesOwnersData]],
    projectData: ProjectData,
    project: Project,
    mutableStorage: MutableEntityStorage
  ): Unit = {
    toImport.asScala.foreach { dataNode =>
      val moduleOpt = findModuleForParentOfDataNode(dataNode)
      moduleOpt.foreach { module =>
        val moduleEntityOpt = mutableStorage.resolveOpt(new ModuleId(module.getName))
        moduleEntityOpt
          .filter(WorkspaceModelUtil.findSharedSourcesOwnersEntityForModuleEntity(_, mutableStorage).isEmpty)
          .foreach { moduleEntity =>
            val sharedSourcesOwnersData = dataNode.getData
            val newEntity = createSharedSourcesOwnersEntity(sharedSourcesOwnersData.ownerModuleIds)
            ModuleEntityAndExtensions.modifyEntity(mutableStorage, moduleEntity, builder => {
              ModuleExtensionWorkspaceEntityKt.setModuleExtensionWorkspaceEntity(builder, newEntity)
              kotlin.Unit.INSTANCE
            })
          }
      }
    }
  }

  private def createSharedSourcesOwnersEntity(ownerModulesIds: JList[String]): SharedSourcesOwnersEntity.Builder = {
    val entitySource = WorkspaceEntitiesCompanionProxy.SharedSourcesOwnersEntitySource
    WorkspaceEntitiesCompanionProxy.SharedSourcesOwnersEntityCompanion.create(ownerModulesIds, entitySource)
  }
}

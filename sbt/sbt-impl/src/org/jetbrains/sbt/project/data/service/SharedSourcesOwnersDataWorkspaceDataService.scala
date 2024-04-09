package org.jetbrains.sbt.project.data.service

import com.intellij.entities.SharedSourcesOwnersEntity
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.WorkspaceDataService
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.{ModuleEntity, ModuleId}
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.sbt.WorkspaceModelUtil
import org.jetbrains.sbt.SbtUtil.EntityStorageOps
import org.jetbrains.sbt.project.SharedSourcesOwnersData
import org.jetbrains.sbt.project.data.findModuleForParentOfDataNode

import java.util
import java.util.{List => JList}
import scala.jdk.CollectionConverters.CollectionHasAsScala

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
            val newEntity = createSharedSourcesOwnersEntity(moduleEntity, sharedSourcesOwnersData.ownerModuleIds)
            mutableStorage.addEntity(newEntity)
          }
      }
    }
  }

  private def createSharedSourcesOwnersEntity(moduleEntity: ModuleEntity, ownerModulesIds: JList[String]): SharedSourcesOwnersEntity = {
    val entitySource = WorkspaceEntitiesCompanionProxy.SharedSourcesOwnersEntitySource
    WorkspaceEntitiesCompanionProxy.SharedSourcesOwnersEntityCompanion.create(ownerModulesIds, entitySource, (t: SharedSourcesOwnersEntity.Builder) => {
      t.setModule(moduleEntity)
      kotlin.Unit.INSTANCE
    })
  }
}

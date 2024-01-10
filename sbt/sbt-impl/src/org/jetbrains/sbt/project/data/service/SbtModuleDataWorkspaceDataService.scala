package org.jetbrains.sbt.project.data.service

import com.intellij.entities.{SbtEntitySource, SbtModuleWSMEntity}
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.WorkspaceDataService
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.{ModuleEntity, ModuleId}
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.VirtualFileUrlManagerUtil
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.SbtUtil.EntityStorageOps
import org.jetbrains.sbt.project.data.{SbtModuleData, findModuleForParentOfDataNode}

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

class SbtModuleDataWorkspaceDataService extends WorkspaceDataService[SbtModuleData]{

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
          .filter(SbtUtil.findSbtModuleWSMEntityForModuleEntity(_, mutableStorage).isEmpty)
          .foreach { moduleEntity =>
            val sbtModuleData = dataNode.getData
            val newEntity = createSbtModuleWSMEntity(sbtModuleData, moduleEntity, project)
            mutableStorage.addEntity(newEntity)
          }
      }
    }
  }

  private def createSbtModuleWSMEntity(sbtModuleData: SbtModuleData, moduleEntity: ModuleEntity, project: Project): SbtModuleWSMEntity =  {
    val vfUrlManager = VirtualFileUrlManagerUtil.getInstance(SbtModuleWSMEntityProxy.VirtualFileUrlManagerCompanion, project)

    val sbtModuleDataUri = sbtModuleData.buildURI.toString
    val buildSbtVirtualFileUrl = vfUrlManager.fromUrl(sbtModuleDataUri + "build.sbt")
    val entitySource = new SbtEntitySource(buildSbtVirtualFileUrl)

    val baseDirectoryVirtualFileUrl = vfUrlManager.fromPath(sbtModuleData.baseDirectory.toString)

    SbtModuleWSMEntityProxy.SbtModuleWSMEntityCompanion.create(sbtModuleData.id, sbtModuleDataUri, baseDirectoryVirtualFileUrl, entitySource, (t: SbtModuleWSMEntity.Builder) => {
      t.setModule(moduleEntity)
      kotlin.Unit.INSTANCE
    })
  }
}


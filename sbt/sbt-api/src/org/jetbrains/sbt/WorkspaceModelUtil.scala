package org.jetbrains.sbt

import com.intellij.entities.{ModuleExtensionWorkspaceEntity, SbtModuleEntity, SharedSourcesOwnersEntity}
import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.{ModuleEntity, ModuleId}
import com.intellij.platform.workspace.storage.EntityStorage

import scala.jdk.CollectionConverters.IteratorHasAsScala

object WorkspaceModelUtil {

  def getSbtModuleEntity(module: Module): Option[SbtModuleEntity] =
    getWorkspaceModelEntityForModule(module, classOf[SbtModuleEntity])

  def getSharedSourcesOwnersEntity(module: Module): Option[SharedSourcesOwnersEntity] =
    getWorkspaceModelEntityForModule(module, classOf[SharedSourcesOwnersEntity])

  def findSbtModuleEntityForModuleEntity(moduleEntity: ModuleEntity, storage: EntityStorage): Option[SbtModuleEntity] =
    findWorkspaceModelEntityForModuleEntity(moduleEntity, storage, classOf[SbtModuleEntity])

  def findSharedSourcesOwnersEntityForModuleEntity(moduleEntity: ModuleEntity, storage: EntityStorage): Option[SharedSourcesOwnersEntity] =
    findWorkspaceModelEntityForModuleEntity(moduleEntity, storage, classOf[SharedSourcesOwnersEntity])

  private def getWorkspaceModelEntityForModule[T <: ModuleExtensionWorkspaceEntity](module: Module, desiredEntityClass: Class[T]) = {
    val project = module.getProject
    val storage = WorkspaceModel.getInstance(project).getCurrentSnapshot
    val moduleEntityOpt = storage.resolve(new ModuleId(module.getName))
    findWorkspaceModelEntityForModuleEntity(moduleEntityOpt, storage, desiredEntityClass)
  }

  private def findWorkspaceModelEntityForModuleEntity[T <: ModuleExtensionWorkspaceEntity](
    moduleEntity: ModuleEntity,
    storage: EntityStorage,
    desiredEntityClass: Class[T]
  ): Option[T] = {
    val entities = storage.entities(desiredEntityClass).iterator().asScala.toList
    entities.find(_.getModule == moduleEntity)
  }
}

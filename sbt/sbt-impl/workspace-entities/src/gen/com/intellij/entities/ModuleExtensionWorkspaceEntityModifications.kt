@file:JvmName("ModuleExtensionWorkspaceEntityModifications")

package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface ModuleExtensionWorkspaceEntityBuilder<T : ModuleExtensionWorkspaceEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
}

var ModuleEntityBuilder.moduleExtensionWorkspaceEntity: ModuleExtensionWorkspaceEntityBuilder<out ModuleExtensionWorkspaceEntity>
  by WorkspaceEntity.extensionBuilder(ModuleExtensionWorkspaceEntity::class.java)


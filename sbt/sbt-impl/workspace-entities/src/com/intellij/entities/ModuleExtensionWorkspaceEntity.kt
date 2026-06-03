package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

/**
 * Abstract entity that other entities that need to have extension property to ModuleEntity can inherit from.
 */
@Abstract
interface ModuleExtensionWorkspaceEntity : WorkspaceEntity {
  @Parent
  val module: ModuleEntity
}

val ModuleEntity.moduleExtensionWorkspaceEntity: ModuleExtensionWorkspaceEntity
  by WorkspaceEntity.extension()

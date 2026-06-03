@file:JvmName("ModuleExtensionWorkspaceEntityModifications")

package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ModuleExtensionWorkspaceEntityBuilder<T : ModuleExtensionWorkspaceEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
}

internal object ModuleExtensionWorkspaceEntityType : EntityType<ModuleExtensionWorkspaceEntity, ModuleExtensionWorkspaceEntityBuilder<ModuleExtensionWorkspaceEntity>>() {
  override val entityClass: Class<ModuleExtensionWorkspaceEntity> get() = ModuleExtensionWorkspaceEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModuleExtensionWorkspaceEntityBuilder<ModuleExtensionWorkspaceEntity>.() -> Unit)? = null,
  ): ModuleExtensionWorkspaceEntityBuilder<ModuleExtensionWorkspaceEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

var ModuleEntityBuilder.moduleExtensionWorkspaceEntity: ModuleExtensionWorkspaceEntityBuilder<out ModuleExtensionWorkspaceEntity>
  by WorkspaceEntity.extensionBuilder(ModuleExtensionWorkspaceEntity::class.java)


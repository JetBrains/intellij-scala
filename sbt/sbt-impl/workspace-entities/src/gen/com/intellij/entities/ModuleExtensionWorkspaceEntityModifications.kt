@file:JvmName("ModuleExtensionWorkspaceEntityModifications")

package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModuleExtensionWorkspaceEntityBuilder<T : ModuleExtensionWorkspaceEntity> : WorkspaceEntityBuilder<T> {
    override var entitySource: EntitySource
    var module: ModuleEntityBuilder
}

internal object ModuleExtensionWorkspaceEntityType :
    EntityType<ModuleExtensionWorkspaceEntity, ModuleExtensionWorkspaceEntityBuilder<ModuleExtensionWorkspaceEntity>>() {
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


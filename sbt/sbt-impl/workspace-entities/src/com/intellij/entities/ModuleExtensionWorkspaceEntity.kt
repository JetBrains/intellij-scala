package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child

@Abstract
interface ModuleExtensionWorkspaceEntity: WorkspaceEntity {
    val module: ModuleEntity

    //region generated code
    @GeneratedCodeApiVersion(2)
    interface Builder<T : ModuleExtensionWorkspaceEntity> : ModuleExtensionWorkspaceEntity, WorkspaceEntity.Builder<T> {
        override var entitySource: EntitySource
        override var module: ModuleEntity
    }

    companion object : EntityType<ModuleExtensionWorkspaceEntity, Builder<ModuleExtensionWorkspaceEntity>>() {
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(
            entitySource: EntitySource,
            init: (Builder<ModuleExtensionWorkspaceEntity>.() -> Unit)? = null
        ): ModuleExtensionWorkspaceEntity {
            val builder = builder()
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
//endregion
}

//region generated code
var ModuleEntity.Builder.moduleExtensionWorkspaceEntity: @Child ModuleExtensionWorkspaceEntity
        by WorkspaceEntity.extension()
//endregion


val ModuleEntity.moduleExtensionWorkspaceEntity: @Child ModuleExtensionWorkspaceEntity
        by WorkspaceEntity.extension()

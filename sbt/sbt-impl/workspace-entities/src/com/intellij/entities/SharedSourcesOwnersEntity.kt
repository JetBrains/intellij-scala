package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

interface SharedSourcesOwnersEntity: ModuleExtensionWorkspaceEntity {
    val ownerModuleIds: List<String>

    //region generated code
    @GeneratedCodeApiVersion(3)
    interface Builder : WorkspaceEntity.Builder<SharedSourcesOwnersEntity>,
        ModuleExtensionWorkspaceEntity.Builder<SharedSourcesOwnersEntity> {
        override var entitySource: EntitySource
        override var module: ModuleEntity.Builder
        var ownerModuleIds: MutableList<String>
    }

    companion object : EntityType<SharedSourcesOwnersEntity, Builder>(ModuleExtensionWorkspaceEntity) {
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(
            ownerModuleIds: List<String>,
            entitySource: EntitySource,
            init: (Builder.() -> Unit)? = null,
        ): Builder {
            val builder = builder()
            builder.ownerModuleIds = ownerModuleIds.toMutableWorkspaceList()
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
//endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(
    entity: SharedSourcesOwnersEntity,
    modification: SharedSourcesOwnersEntity.Builder.() -> Unit,
): SharedSourcesOwnersEntity = modifyEntity(SharedSourcesOwnersEntity.Builder::class.java, entity, modification)
//endregion

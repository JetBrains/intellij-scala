package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface SbtModuleEntity: ModuleExtensionWorkspaceEntity {
    val sbtModuleId: String
    val buildURI: String
    val baseDirectory: VirtualFileUrl

    //region generated code
    @GeneratedCodeApiVersion(3)
    interface Builder : WorkspaceEntity.Builder<SbtModuleEntity>,
        ModuleExtensionWorkspaceEntity.Builder<SbtModuleEntity> {
        override var entitySource: EntitySource
        override var module: ModuleEntity.Builder
        var sbtModuleId: String
        var buildURI: String
        var baseDirectory: VirtualFileUrl
    }

    companion object : EntityType<SbtModuleEntity, Builder>(ModuleExtensionWorkspaceEntity) {
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(
            sbtModuleId: String,
            buildURI: String,
            baseDirectory: VirtualFileUrl,
            entitySource: EntitySource,
            init: (Builder.() -> Unit)? = null,
        ): Builder {
            val builder = builder()
            builder.sbtModuleId = sbtModuleId
            builder.buildURI = buildURI
            builder.baseDirectory = baseDirectory
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
//endregion
}

//region generated code
fun MutableEntityStorage.modifySbtModuleEntity(
    entity: SbtModuleEntity,
    modification: SbtModuleEntity.Builder.() -> Unit,
): SbtModuleEntity {
    return modifyEntity(SbtModuleEntity.Builder::class.java, entity, modification)
}
//endregion

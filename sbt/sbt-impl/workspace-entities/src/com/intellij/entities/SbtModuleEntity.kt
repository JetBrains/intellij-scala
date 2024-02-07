package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface SbtModuleEntity: WorkspaceEntity {
    val sbtModuleId: String
    val buildURI: String
    val baseDirectory: VirtualFileUrl
    val module: ModuleEntity

    //region generated code
    @GeneratedCodeApiVersion(2)
    interface Builder : SbtModuleEntity, WorkspaceEntity.Builder<SbtModuleEntity> {
        override var entitySource: EntitySource
        override var sbtModuleId: String
        override var buildURI: String
        override var baseDirectory: VirtualFileUrl
        override var module: ModuleEntity
    }

    companion object : EntityType<SbtModuleEntity, Builder>() {
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(sbtModuleId: String, buildURI: String, baseDirectory: VirtualFileUrl, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SbtModuleEntity {
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
fun MutableEntityStorage.modifyEntity(entity: SbtModuleEntity, modification: SbtModuleEntity.Builder.() -> Unit): SbtModuleEntity = modifyEntity(SbtModuleEntity.Builder::class.java, entity, modification)
var ModuleEntity.Builder.sbtModuleEntity: @Child SbtModuleEntity
        by WorkspaceEntity.extension()
//endregion

val ModuleEntity.sbtModuleEntity: @Child SbtModuleEntity
        by WorkspaceEntity.extension()


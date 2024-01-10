package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface SbtModuleWSMEntity: WorkspaceEntity {
    val sbtModuleId: String
    val buildURI: String
    val baseDirectory: VirtualFileUrl
    val module: ModuleEntity

    //region generated code
    @GeneratedCodeApiVersion(2)
    interface Builder : SbtModuleWSMEntity, WorkspaceEntity.Builder<SbtModuleWSMEntity> {
        override var entitySource: EntitySource
        override var sbtModuleId: String
        override var buildURI: String
        override var baseDirectory: VirtualFileUrl
        override var module: ModuleEntity
    }

    companion object : EntityType<SbtModuleWSMEntity, Builder>() {
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(sbtModuleId: String, buildURI: String, baseDirectory: VirtualFileUrl, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SbtModuleWSMEntity {
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
fun MutableEntityStorage.modifyEntity(entity: SbtModuleWSMEntity, modification: SbtModuleWSMEntity.Builder.() -> Unit): SbtModuleWSMEntity = modifyEntity(SbtModuleWSMEntity.Builder::class.java, entity, modification)
var ModuleEntity.Builder.sbtModuleWSMEntity: @Child SbtModuleWSMEntity
        by WorkspaceEntity.extension()
//endregion

val ModuleEntity.sbtModuleWSMEntity: @Child SbtModuleWSMEntity
        by WorkspaceEntity.extension()


@file:JvmName("SbtModuleEntityModifications")

package com.intellij.entities

import com.intellij.entities.impl.SbtModuleEntityImpl
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface SbtModuleEntityBuilder : WorkspaceEntityBuilder<SbtModuleEntity>, ModuleExtensionWorkspaceEntityBuilder<SbtModuleEntity> {
  override var entitySource: EntitySource
  override var module: ModuleEntityBuilder
  var sbtModuleId: String
  var buildURI: String
  var baseDirectory: VirtualFileUrl
}

internal object SbtModuleEntityType : EntityType<SbtModuleEntity, SbtModuleEntityBuilder>() {
  override val entityImplClass: Class<*> get() = SbtModuleEntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = SbtModuleEntityImpl.Builder::class.java
  operator fun invoke(
    sbtModuleId: String,
    buildURI: String,
    baseDirectory: VirtualFileUrl,
    entitySource: EntitySource,
    init: (SbtModuleEntityBuilder.() -> Unit)? = null,
  ): SbtModuleEntityBuilder {
    val builder = builder()
    builder.sbtModuleId = sbtModuleId
    builder.buildURI = buildURI
    builder.baseDirectory = baseDirectory
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySbtModuleEntity(
  entity: SbtModuleEntity,
  modification: SbtModuleEntityBuilder.() -> Unit,
): SbtModuleEntity = modifyEntity(SbtModuleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSbtModuleEntity")
fun SbtModuleEntity(
  sbtModuleId: String,
  buildURI: String,
  baseDirectory: VirtualFileUrl,
  entitySource: EntitySource,
  init: (SbtModuleEntityBuilder.() -> Unit)? = null,
): SbtModuleEntityBuilder = SbtModuleEntityType(sbtModuleId, buildURI, baseDirectory, entitySource, init)

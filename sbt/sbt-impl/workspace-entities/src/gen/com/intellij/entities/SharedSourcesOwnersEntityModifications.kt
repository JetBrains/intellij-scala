@file:JvmName("SharedSourcesOwnersEntityModifications")

package com.intellij.entities

import com.intellij.entities.impl.SharedSourcesOwnersEntityImpl
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface SharedSourcesOwnersEntityBuilder : WorkspaceEntityBuilder<SharedSourcesOwnersEntity>, ModuleExtensionWorkspaceEntityBuilder<SharedSourcesOwnersEntity> {
  override var entitySource: EntitySource
  override var module: ModuleEntityBuilder
  var ownerModuleIds: MutableList<String>
}

internal object SharedSourcesOwnersEntityType : EntityType<SharedSourcesOwnersEntity, SharedSourcesOwnersEntityBuilder>() {
  override val entityImplClass: Class<*> get() = SharedSourcesOwnersEntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = SharedSourcesOwnersEntityImpl.Builder::class.java
  operator fun invoke(
    ownerModuleIds: List<String>,
    entitySource: EntitySource,
    init: (SharedSourcesOwnersEntityBuilder.() -> Unit)? = null,
  ): SharedSourcesOwnersEntityBuilder {
    val builder = builder()
    builder.ownerModuleIds = ownerModuleIds.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySharedSourcesOwnersEntity(
  entity: SharedSourcesOwnersEntity,
  modification: SharedSourcesOwnersEntityBuilder.() -> Unit,
): SharedSourcesOwnersEntity = modifyEntity(SharedSourcesOwnersEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSharedSourcesOwnersEntity")
fun SharedSourcesOwnersEntity(
  ownerModuleIds: List<String>,
  entitySource: EntitySource,
  init: (SharedSourcesOwnersEntityBuilder.() -> Unit)? = null,
): SharedSourcesOwnersEntityBuilder = SharedSourcesOwnersEntityType(ownerModuleIds, entitySource, init)

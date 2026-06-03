@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.entities.impl

import com.intellij.entities.ModuleExtensionWorkspaceEntity
import com.intellij.entities.SharedSourcesOwnersEntity
import com.intellij.entities.SharedSourcesOwnersEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SharedSourcesOwnersEntityImpl(private val dataSource: SharedSourcesOwnersEntityData) : SharedSourcesOwnersEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ModuleExtensionWorkspaceEntity::class.java, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID)

  }

  override val module: ModuleEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity
      ?: error("Parent module not found for ModuleExtensionWorkspaceEntity")
  override val ownerModuleIds: List<String>
    get() {
      readField("ownerModuleIds")
      return dataSource.ownerModuleIds
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: SharedSourcesOwnersEntityData?) : ModifiableWorkspaceEntityBase<SharedSourcesOwnersEntity, SharedSourcesOwnersEntityData>(result), SharedSourcesOwnersEntityBuilder {
    internal constructor() : this(SharedSourcesOwnersEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        } else {
          error("Entity SharedSourcesOwnersEntity is already created in a different builder")
        }
      }
      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
// After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
// Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null
// Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
          error("Field ModuleExtensionWorkspaceEntity#module should be initialized")
        }
      } else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ModuleExtensionWorkspaceEntity#module should be initialized")
        }
      }
      if (!getEntityData().isOwnerModuleIdsInitialized()) {
        error("Field SharedSourcesOwnersEntity#ownerModuleIds should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_ownerModuleIds = getEntityData().ownerModuleIds
      if (collection_ownerModuleIds is MutableWorkspaceList<*>) {
        collection_ownerModuleIds.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SharedSourcesOwnersEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.ownerModuleIds != dataSource.ownerModuleIds) this.ownerModuleIds = dataSource.ownerModuleIds.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }
    override var module: ModuleEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleEntityBuilder)
            ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] as? ModuleEntityBuilder)
            ?: error("module is null for ModuleExtensionWorkspaceEntity")
        } else {
          (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] as? ModuleEntityBuilder)
            ?: error("module is null for ModuleExtensionWorkspaceEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(MODULE_CONNECTION_ID, value, this)
        } else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    private val ownerModuleIdsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("ownerModuleIds")
    }
    override var ownerModuleIds: MutableList<String>
      get() {
        val collection_ownerModuleIds = getEntityData().ownerModuleIds
        if (collection_ownerModuleIds !is MutableWorkspaceList) return collection_ownerModuleIds
        if (diff == null || modifiable.get()) {
          collection_ownerModuleIds.setModificationUpdateAction(ownerModuleIdsUpdater)
        } else {
          collection_ownerModuleIds.cleanModificationUpdateAction()
        }
        return collection_ownerModuleIds
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).ownerModuleIds = value
        ownerModuleIdsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<SharedSourcesOwnersEntity> = SharedSourcesOwnersEntity::class.java
  }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SharedSourcesOwnersEntityData : WorkspaceEntityData<SharedSourcesOwnersEntity>() {
  lateinit var ownerModuleIds: MutableList<String>

  internal fun isOwnerModuleIdsInitialized(): Boolean = ::ownerModuleIds.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<SharedSourcesOwnersEntity> {
    val modifiable = SharedSourcesOwnersEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorageInstrumentation): SharedSourcesOwnersEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = SharedSourcesOwnersEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.entities.SharedSourcesOwnersEntity") as EntityMetadata
  }

  override fun clone(): SharedSourcesOwnersEntityData {
    val clonedEntity = super.clone()
    clonedEntity as SharedSourcesOwnersEntityData
    clonedEntity.ownerModuleIds = clonedEntity.ownerModuleIds.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SharedSourcesOwnersEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return SharedSourcesOwnersEntity(ownerModuleIds, entitySource) {
      parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SharedSourcesOwnersEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.ownerModuleIds != other.ownerModuleIds) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SharedSourcesOwnersEntityData
    if (this.ownerModuleIds != other.ownerModuleIds) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + ownerModuleIds.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + ownerModuleIds.hashCode()
    return result
  }
}

package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(5)
open class SbtModuleEntityImpl(private val dataSource: SbtModuleEntityData) : SbtModuleEntity,
    WorkspaceEntityBase(dataSource) {

    private companion object {
        internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(
            ModuleEntity::class.java,
            ModuleExtensionWorkspaceEntity::class.java,
            ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
            false
        )

        private val connections = listOf<ConnectionId>(
            MODULE_CONNECTION_ID,
        )

    }

    override val module: ModuleEntity
        get() = snapshot.extractOneToAbstractOneParent(MODULE_CONNECTION_ID, this)!!

    override val sbtModuleId: String
        get() {
            readField("sbtModuleId")
            return dataSource.sbtModuleId
        }

    override val buildURI: String
        get() {
            readField("buildURI")
            return dataSource.buildURI
        }

    override val baseDirectory: VirtualFileUrl
        get() {
            readField("baseDirectory")
            return dataSource.baseDirectory
        }

    override val entitySource: EntitySource
        get() {
            readField("entitySource")
            return dataSource.entitySource
        }

    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }


    class Builder(result: SbtModuleEntityData?) :
        ModifiableWorkspaceEntityBase<SbtModuleEntity, SbtModuleEntityData>(result), SbtModuleEntity.Builder {
        constructor() : this(SbtModuleEntityData())

        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                } else {
                    error("Entity SbtModuleEntity is already created in a different builder")
                }
            }

            this.diff = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
            // Builder may switch to snapshot at any moment and lock entity data to modification
            this.currentEntityData = null

            index(this, "baseDirectory", this.baseDirectory)
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
                if (_diff.extractOneToAbstractOneParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
                    error("Field ModuleExtensionWorkspaceEntity#module should be initialized")
                }
            } else {
                if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
                    error("Field ModuleExtensionWorkspaceEntity#module should be initialized")
                }
            }
            if (!getEntityData().isSbtModuleIdInitialized()) {
                error("Field SbtModuleEntity#sbtModuleId should be initialized")
            }
            if (!getEntityData().isBuildURIInitialized()) {
                error("Field SbtModuleEntity#buildURI should be initialized")
            }
            if (!getEntityData().isBaseDirectoryInitialized()) {
                error("Field SbtModuleEntity#baseDirectory should be initialized")
            }
        }

        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }

        // Relabeling code, move information from dataSource to this builder
        override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
            dataSource as SbtModuleEntity
            if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
            if (this.sbtModuleId != dataSource.sbtModuleId) this.sbtModuleId = dataSource.sbtModuleId
            if (this.buildURI != dataSource.buildURI) this.buildURI = dataSource.buildURI
            if (this.baseDirectory != dataSource.baseDirectory) this.baseDirectory = dataSource.baseDirectory
            updateChildToParentReferences(parents)
        }


        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData(true).entitySource = value
                changedProperty.add("entitySource")

            }

        override var module: ModuleEntity.Builder
            get() {
                val _diff = diff
                return if (_diff != null) {
                    @OptIn(EntityStorageInstrumentationApi::class)
                    ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(
                        MODULE_CONNECTION_ID,
                        this
                    ) as? ModuleEntity.Builder)
                        ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder)
                } else {
                    this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder
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
                    _diff.updateOneToAbstractOneParentOfChild(MODULE_CONNECTION_ID, this, value)
                } else {
                    if (value is ModifiableWorkspaceEntityBase<*, *>) {
                        value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable

                    this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
                }
                changedProperty.add("module")
            }

        override var sbtModuleId: String
            get() = getEntityData().sbtModuleId
            set(value) {
                checkModificationAllowed()
                getEntityData(true).sbtModuleId = value
                changedProperty.add("sbtModuleId")
            }

        override var buildURI: String
            get() = getEntityData().buildURI
            set(value) {
                checkModificationAllowed()
                getEntityData(true).buildURI = value
                changedProperty.add("buildURI")
            }

        override var baseDirectory: VirtualFileUrl
            get() = getEntityData().baseDirectory
            set(value) {
                checkModificationAllowed()
                getEntityData(true).baseDirectory = value
                changedProperty.add("baseDirectory")
                val _diff = diff
                if (_diff != null) index(this, "baseDirectory", value)
            }

        override fun getEntityClass(): Class<SbtModuleEntity> = SbtModuleEntity::class.java
    }
}

class SbtModuleEntityData : WorkspaceEntityData<SbtModuleEntity>() {
    lateinit var sbtModuleId: String
    lateinit var buildURI: String
    lateinit var baseDirectory: VirtualFileUrl

    internal fun isSbtModuleIdInitialized(): Boolean = ::sbtModuleId.isInitialized
    internal fun isBuildURIInitialized(): Boolean = ::buildURI.isInitialized
    internal fun isBaseDirectoryInitialized(): Boolean = ::baseDirectory.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SbtModuleEntity> {
        val modifiable = SbtModuleEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.id = createEntityId()
        return modifiable
    }

    @OptIn(EntityStorageInstrumentationApi::class)
    override fun createEntity(snapshot: EntityStorageInstrumentation): SbtModuleEntity {
        val entityId = createEntityId()
        return snapshot.initializeEntity(entityId) {
            val entity = SbtModuleEntityImpl(this)
            entity.snapshot = snapshot
            entity.id = entityId
            entity
        }
    }

    override fun getMetadata(): EntityMetadata {
        return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.entities.SbtModuleEntity") as EntityMetadata
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return SbtModuleEntity::class.java
    }

    override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
        return SbtModuleEntity(sbtModuleId, buildURI, baseDirectory, entitySource) {
            parents.filterIsInstance<ModuleEntity.Builder>().singleOrNull()?.let { this.module = it }
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

        other as SbtModuleEntityData

        if (this.entitySource != other.entitySource) return false
        if (this.sbtModuleId != other.sbtModuleId) return false
        if (this.buildURI != other.buildURI) return false
        if (this.baseDirectory != other.baseDirectory) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false

        other as SbtModuleEntityData

        if (this.sbtModuleId != other.sbtModuleId) return false
        if (this.buildURI != other.buildURI) return false
        if (this.baseDirectory != other.baseDirectory) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + sbtModuleId.hashCode()
        result = 31 * result + buildURI.hashCode()
        result = 31 * result + baseDirectory.hashCode()
        return result
    }

    override fun hashCodeIgnoringEntitySource(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + sbtModuleId.hashCode()
        result = 31 * result + buildURI.hashCode()
        result = 31 * result + baseDirectory.hashCode()
        return result
    }
}

package com.intellij.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(3)
open class SbtModuleWSMEntityImpl(private val dataSource: SbtModuleWSMEntityData) : SbtModuleWSMEntity, WorkspaceEntityBase(dataSource) {

    private companion object {
        internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, SbtModuleWSMEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)

        private val connections = listOf<ConnectionId>(
                MODULE_CONNECTION_ID,
        )

    }

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

    override val module: ModuleEntity
        get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!

    override val entitySource: EntitySource
        get() {
            readField("entitySource")
            return dataSource.entitySource
        }

    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }


    class Builder(result: SbtModuleWSMEntityData?) : ModifiableWorkspaceEntityBase<SbtModuleWSMEntity, SbtModuleWSMEntityData>(result), SbtModuleWSMEntity.Builder {
        constructor() : this(SbtModuleWSMEntityData())

        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                } else {
                    error("Entity SbtModuleWSMEntity is already created in a different builder")
                }
            }

            this.diff = builder
            this.snapshot = builder
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
            if (!getEntityData().isSbtModuleIdInitialized()) {
                error("Field SbtModuleWSMEntity#sbtModuleId should be initialized")
            }
            if (!getEntityData().isBuildURIInitialized()) {
                error("Field SbtModuleWSMEntity#buildURI should be initialized")
            }
            if (!getEntityData().isBaseDirectoryInitialized()) {
                error("Field SbtModuleWSMEntity#baseDirectory should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
                    error("Field SbtModuleWSMEntity#module should be initialized")
                }
            } else {
                if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
                    error("Field SbtModuleWSMEntity#module should be initialized")
                }
            }
        }

        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }

        // Relabeling code, move information from dataSource to this builder
        override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
            dataSource as SbtModuleWSMEntity
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

        override var module: ModuleEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneParent(MODULE_CONNECTION_ID, this)
                            ?: this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity
                } else {
                    this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity
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
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
                    _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
                } else {
                    if (value is ModifiableWorkspaceEntityBase<*, *>) {
                        value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable

                    this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
                }
                changedProperty.add("module")
            }

        override fun getEntityClass(): Class<SbtModuleWSMEntity> = SbtModuleWSMEntity::class.java
    }
}

class SbtModuleWSMEntityData : WorkspaceEntityData<SbtModuleWSMEntity>() {
    lateinit var sbtModuleId: String
    lateinit var buildURI: String
    lateinit var baseDirectory: VirtualFileUrl

    internal fun isSbtModuleIdInitialized(): Boolean = ::sbtModuleId.isInitialized
    internal fun isBuildURIInitialized(): Boolean = ::buildURI.isInitialized
    internal fun isBaseDirectoryInitialized(): Boolean = ::baseDirectory.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SbtModuleWSMEntity> {
        val modifiable = SbtModuleWSMEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.snapshot = diff
        modifiable.id = createEntityId()
        return modifiable
    }

    @OptIn(EntityStorageInstrumentationApi::class)
    override fun createEntity(snapshot: EntityStorageInstrumentation): SbtModuleWSMEntity {
        val entityId = createEntityId()
        return snapshot.initializeEntity(entityId) {
            val entity = SbtModuleWSMEntityImpl(this)
            entity.snapshot = snapshot
            entity.id = entityId
            entity
        }
    }

    override fun getMetadata(): EntityMetadata {
        return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.entities.SbtModuleWSMEntity") as EntityMetadata
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return SbtModuleWSMEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
        return SbtModuleWSMEntity(sbtModuleId, buildURI, baseDirectory, entitySource) {
            parents.filterIsInstance<ModuleEntity>().singleOrNull()?.let { this.module = it }
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

        other as SbtModuleWSMEntityData

        if (this.entitySource != other.entitySource) return false
        if (this.sbtModuleId != other.sbtModuleId) return false
        if (this.buildURI != other.buildURI) return false
        if (this.baseDirectory != other.baseDirectory) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false

        other as SbtModuleWSMEntityData

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

package org.jetbrains.plugins.scala.compiler.references

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.{FileType, FileTypeRegistry}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.{ModificationTracker, UserDataHolderBase}
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events._
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.platform.backend.workspace.{WorkspaceModelChangeListener, WorkspaceModelTopics}
import com.intellij.platform.workspace.jps.entities.{ContentRootEntity, ModuleEntity}
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiModificationTracker}
import com.intellij.util.containers.ContainerUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project._

import java.util
import java.util.concurrent.locks.{Lock, ReentrantLock}
import scala.jdk.CollectionConverters._

/**
 * Mostly copy-pasted from [[com.intellij.compiler.backwardRefs.DirtyScopeHolder]], but modified to be able
 * to work with abstract [[Scope]]s (e.g. sbt project scoped to a particular configuration foo / Compile) instead of just IDEA modules.
 *
 * See also [[ScalaDirtyScopeHolder]].
 */
abstract class DirtyScopeHolder[Scope](
  project: Project,
  fileTypes: Array[FileType],
  fileIndex: ProjectFileIndex,
  fileDocManager: FileDocumentManager,
  psiDocManager: PsiDocumentManager,
  modificationTracker: ModificationTracker
) extends UserDataHolderBase with BulkFileListener {

  protected val lock: Lock = new ReentrantLock()
  protected val fileTypeRegistry: FileTypeRegistry = FileTypeRegistry.getInstance()
  protected val vfsChangedScopes: util.Set[Scope] = new util.HashSet()
  protected val modifiedDuringIndexing: util.HashMap[Scope, Int] = new util.HashMap[Scope, Int]()
  protected val compilationAffectedScopes: util.Set[Scope] = ContainerUtil.newConcurrentSet[Scope]()
  protected var indexingPhases: Int = 0

  protected def scopeForSourceContentFile(vFile: VirtualFile): Set[Scope]
  protected def moduleScopes(module: Module): Set[Scope]
  protected def scopeToSearchScope(scope: Scope): GlobalSearchScope

  private[references] def markScopeUpToDate(scope: Scope): Unit = {
    compilationAffectedScopes.add(scope)
  }

  private[references] def markProjectAsOutdated(): Unit = lock.withLock {
    sourceModules.foreach(markModuleAsDirty)
  }

  private[references] def reset(): Unit = lock.withLock {
    markProjectAsOutdated()
    modifiedDuringIndexing.clear()
    compilationAffectedScopes.clear()
    indexingPhases = 0
  }

  override def after(events: util.List[_ <: VFileEvent]): Unit = events.forEach {
    case event @ (_: VFileCreateEvent | _: VFileMoveEvent | _: VFileCopyEvent) =>
      onFileChange(event.getFile)
    case event: VFilePropertyChangeEvent =>
      if (isRenameEvent(event)) {
        onFileChange(event.getFile)
      }
    case _ => ()
  }

  override def before(events: util.List[_ <: VFileEvent]): Unit = events.forEach {
    case event @ (_: VFileDeleteEvent | _: VFileMoveEvent | _: VFileContentChangeEvent) =>
      onFileChange(event.getFile)
    case _ => ()
  }

  private def isRenameEvent(event: VFilePropertyChangeEvent) = {
    val propertyName = event.getPropertyName
    propertyName == VirtualFile.PROP_NAME || propertyName == VirtualFile.PROP_SYMLINK_TARGET
  }

  private def onFileChange(@Nullable vFile: VirtualFile): Unit = {
    if (vFile != null) {
      val scope = scopeForSourceContentFile(vFile)
      addToDirtyScopes(scope)
    }
  }

  protected def markModuleAsDirty(module: Module): Unit = lock.withLock {
    val scopes = moduleScopes(module)
    addToDirtyScopes(scopes)
  }

  protected def addToDirtyScopes(scopes: Set[Scope]): Unit = lock.withLock {
    if (indexingPhases != 0) {
      scopes.foreach { scope =>
        modifiedDuringIndexing.merge(scope, indexingPhases, Math.max(_, _))
      }
    }
    else {
      vfsChangedScopes.addAll(scopes.asJava)
    }
  }

  private[references] def indexingStarted(): Unit = lock.withLock {
    indexingPhases += 1
  }

  private[references] def indexingFinished(): Unit = lock.withLock {
    indexingPhases -= 1
    vfsChangedScopes.removeAll(compilationAffectedScopes)
    compilationAffectedScopes.clear()

    val iter = modifiedDuringIndexing.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      entry.setValue(entry.getValue - indexingPhases)

      if (entry.getValue == 0) {
        iter.remove()
      }
      else {
        addToDirtyScopes(Set(entry.getKey))
      }
    }
  }

  private[references] def installVFSListener(): Unit = {
    val connection = project.getMessageBus.connect(project.unloadAwareDisposable)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, this)
  }

  def dirtyScope: GlobalSearchScope = inReadAction {
    lock.withLock {
      if (indexingPhases != 0)
        GlobalSearchScope.allScope(project)
      else if (!project.isDisposed) {
        val cachedManager = CachedValuesManager.getManager(project)
        cachedManager.getCachedValue(
          this,
          () =>
            CachedValueProvider.Result.create(
              calcDirtyScope(),
              PsiModificationTracker.MODIFICATION_COUNT,
              VirtualFileManager.getInstance(),
              modificationTracker
            )
        )
      }
      else GlobalSearchScope.EMPTY_SCOPE
    }
  }

  private[this] def calcDirtyScope(): GlobalSearchScope =
    if (dirtyScopes.isEmpty) GlobalSearchScope.EMPTY_SCOPE
    else                     GlobalSearchScope.union(dirtyScopes.map(scopeToSearchScope).asJavaCollection)

  def dirtyScopes: Set[Scope] = {
    val dirty = Set.newBuilder[Scope]
    dirty ++= vfsChangedScopes.asScala

    val unsavedDocuments = fileDocManager.getUnsavedDocuments
    unsavedDocuments.foreach { doc =>
      for {
        file  <- fileDocManager.getFile(doc).toOption
        scope <- scopeForSourceContentFile(file)
      } {
        dirty += scope
      }
    }

    val uncommittedDocuments = psiDocManager.getUncommittedDocuments
    uncommittedDocuments.foreach { doc =>
      for {
        pFile <- psiDocManager.getPsiFile(doc).toOption
        vFile <- pFile.getVirtualFile.toOption
        scope <- scopeForSourceContentFile(vFile)
      } {
        dirty += scope
      }
    }

    dirty.result()
  }

  private def sourceModules: Seq[Module] =
    ModuleManager.getInstance(project).getModules.filter(_.isSourceModule).toSeq

  /**
   * copied from [[com.intellij.compiler.backwardRefs.DirtyScopeHolder]]
   */
  //noinspection UnstableApiUsage
  locally {
    val moduleChangeListener = new WorkspaceModelChangeListener() {
      override def beforeChanged(event: VersionedStorageChange): Unit = {
        event.getChanges(classOf[ModuleEntity]).iterator().asScala.foreach { change =>
          for {
            entity <- change.getOldEntity.toOption
            module <- ModuleEntityUtils.findModule(entity, event.getStorageBefore).toOption
          } markModuleAsDirty(module)
        }

        event.getChanges(classOf[ContentRootEntity]).iterator().asScala.foreach { change =>
          for {
            entity <- change.getOldEntity.toOption
            module <- ModuleEntityUtils.findModule(entity.getModule, event.getStorageBefore).toOption
          } markModuleAsDirty(module)
        }
      }

      override def changed(event: VersionedStorageChange): Unit = {
        event.getChanges(classOf[ModuleEntity]).iterator().asScala.foreach { change =>
          for {
            entity <- change.getNewEntity.toOption
            module <- ModuleEntityUtils.findModule(entity, event.getStorageBefore).toOption
          } markModuleAsDirty(module)
        }

        event.getChanges(classOf[ContentRootEntity]).iterator().asScala.foreach { change =>
          for {
            entity <- change.getNewEntity.toOption
            module <- ModuleEntityUtils.findModule(entity.getModule, event.getStorageBefore).toOption
          } markModuleAsDirty(module)
        }
      }
    }

    val connection = project.getMessageBus.connect(project.unloadAwareDisposable)
    connection.subscribe(WorkspaceModelTopics.CHANGED, moduleChangeListener)
  }
}

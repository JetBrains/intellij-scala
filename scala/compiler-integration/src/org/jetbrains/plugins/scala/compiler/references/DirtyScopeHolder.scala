package org.jetbrains.plugins.scala.compiler.references

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.{FileType, FileTypeRegistry}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.{ModificationTracker, UserDataHolderBase}
import com.intellij.openapi.vfs.newvfs.events._
import com.intellij.openapi.vfs.{AsyncFileListener, VirtualFile, VirtualFileManager}
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
) extends UserDataHolderBase with AsyncFileListener {

  protected val lock: Lock = new ReentrantLock()
  protected val fileTypeRegistry: FileTypeRegistry = FileTypeRegistry.getInstance()
  protected val vfsChangedScopes: util.Set[Scope] = new util.HashSet()
  protected val modifiedDuringIndexing: util.HashMap[Scope, Int] = new util.HashMap[Scope, Int]()
  protected val compilationAffectedScopes: util.Set[Scope] = ContainerUtil.newConcurrentSet[Scope]()
  protected var indexingPhases: Int = 0

  protected def scopeForSourceContentFile(vFile: VirtualFile): Option[Scope]
  protected def moduleScopes(module: Module): Set[Scope]
  protected def scopeToSearchScope(scope: Scope): GlobalSearchScope
  protected def calculateDependentScopes(scopes: Set[Scope]): Set[Scope]

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

  override def prepareChange(events: util.List[_ <: VFileEvent]): AsyncFileListener.ChangeApplier = {
    if (project.isDisposed) return null
    val dirty = scopesToBeMarkedDirtyBefore(events)

    new AsyncFileListener.ChangeApplier {
      override def beforeVfsChange(): Unit = {
        addToDirtyScopes(dirty)
      }

      override def afterVfsChange(): Unit = {
        if (project.isDisposed) return
        after(events)
      }
    }
  }

  private def scopesToBeMarkedDirtyBefore(events: util.List[_ <: VFileEvent]): Set[Scope] = {
    val dirty = Set.newBuilder[Scope]

    events.forEach { event =>
      ProgressManager.checkCanceled()

      event match {
        case _: VFileDeleteEvent | _: VFileMoveEvent | _: VFileContentChangeEvent =>
          dirty ++= scopeForChangedFile(event.getFile)
        case _ => ()
      }
    }

    dirty.result()
  }

  private def after(events: util.List[_ <: VFileEvent]): Unit = events.forEach {
    case event @ (_: VFileCreateEvent | _: VFileMoveEvent | _: VFileCopyEvent) =>
      onFileChange(event.getFile)
    case event: VFilePropertyChangeEvent =>
      if (isRenameEvent(event)) {
        onFileChange(event.getFile)
      }
    case _ => ()
  }

  private def isRenameEvent(event: VFilePropertyChangeEvent) = {
    val propertyName = event.getPropertyName
    propertyName == VirtualFile.PROP_NAME || propertyName == VirtualFile.PROP_SYMLINK_TARGET
  }

  private def scopeForChangedFile(@Nullable vFile: VirtualFile): Option[Scope] =
    Option(vFile).flatMap(scopeForSourceContentFile)

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

  protected def addToDirtyScopes(scopes: Iterable[Scope]): Unit = lock.withLock {
    if (indexingPhases != 0) {
      scopes.foreach { scope =>
        modifiedDuringIndexing.merge(scope, indexingPhases, Math.max(_, _))
      }
    } else {
      vfsChangedScopes.addAll(scopes.asJavaCollection)
    }
  }

  private[references] def indexingStarted(): Unit = lock.withLock {
    indexingPhases += 1
  }

  private[references] def indexingFinished(): Unit =
    ReadAction.nonBlocking[Unit] { () =>
      lock.withLock {
        // Because we no longer compute the dependent scopes when reacting to VFS events, the vfsChangedScopes map
        // holds only scopes where files have been directly edited.
        // However, during compilation, more scopes could have been recompiled, and are therefore no longer dirty.
        // But we also need to account for the edge case where only the root module is recompiled, but its dependent
        // modules aren't. In this case, we can make the mistake of clearing _all_ vfsChangedScopes (which the root
        // scope is a part of and therefore implicitly clearing its dependent scopes).
        // So, the solution is to calculate the transitive dependents of the root module, fully expand the
        // vfsChangedScopes set, and then subtract all recompiled scopes. Luckily, this case is covered by a test.
        val dependent = calculateDependentScopes(vfsChangedScopes.asScala.toSet)

        // Run the rest in a non-cancellable section, otherwise the non-blocking read action loses idempotency.
        ProgressManager.getInstance().executeNonCancelableSection { () =>
          indexingPhases -= 1
          vfsChangedScopes.addAll(dependent.asJava)
          vfsChangedScopes.removeAll(compilationAffectedScopes)
          compilationAffectedScopes.clear()

          val iter = modifiedDuringIndexing.entrySet().iterator()
          while (iter.hasNext) {
            val entry = iter.next()
            entry.setValue(entry.getValue - indexingPhases)

            if (entry.getValue == 0) {
              iter.remove()
            } else {
              addToDirtyScopes(Some(entry.getKey))
            }
          }
        }
      }
    }.expireWith(project.unloadAwareDisposable).expireWhen(() => project.isDisposed).executeSynchronously()

  private[references] def installVFSListener(parentDisposable: Disposable): Unit = {
    VirtualFileManager.getInstance().addAsyncFileListener(this, parentDisposable)
  }

  def dirtyScope: GlobalSearchScope = ReadAction.nonBlocking[GlobalSearchScope] { () =>
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
  }.expireWith(project.unloadAwareDisposable).expireWhen(() => project.isDisposed).executeSynchronously()

  private[this] def calcDirtyScope(): GlobalSearchScope = {
    val dirty = dirtyScopes
    if (dirty.isEmpty) GlobalSearchScope.EMPTY_SCOPE
    else GlobalSearchScope.union(dirty.map(scopeToSearchScope).asJavaCollection)
  }

  private[references] def dirtyScopes: Set[Scope] = {
    ProgressManager.checkCanceled()
    val dirty = Set.newBuilder[Scope]
    dirty ++= vfsChangedScopes.asScala

    val unsavedDocuments = fileDocManager.getUnsavedDocuments
    unsavedDocuments.foreach { doc =>
      ProgressManager.checkCanceled()
      for {
        file  <- fileDocManager.getFile(doc).toOption
        scope <- scopeForSourceContentFile(file)
      } {
        dirty += scope
      }
    }

    val uncommittedDocuments = psiDocManager.getUncommittedDocuments
    uncommittedDocuments.foreach { doc =>
      ProgressManager.checkCanceled()
      for {
        pFile <- psiDocManager.getPsiFile(doc).toOption
        vFile <- pFile.getVirtualFile.toOption
        scope <- scopeForSourceContentFile(vFile)
      } {
        dirty += scope
      }
    }

    calculateDependentScopes(dirty.result())
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

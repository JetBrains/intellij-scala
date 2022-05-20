package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.{FileType, FileTypeRegistry}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.{ModificationTracker, UserDataHolderBase}
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events._
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiModificationTracker}
import com.intellij.util.containers.ContainerUtil
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
  project:             Project,
  fileTypes:           Array[FileType],
  fileIndex:           ProjectFileIndex,
  fileDocManager:      FileDocumentManager,
  psiDocManager:       PsiDocumentManager,
  modificationTracker: ModificationTracker
) extends UserDataHolderBase with BulkFileListener {
  protected val lock: Lock                                       = new ReentrantLock()
  protected val fileTypeRegistry: FileTypeRegistry               = FileTypeRegistry.getInstance()
  protected val vfsChangedScopes: util.Set[Scope]                = ContainerUtil.set[Scope]()
  protected val modifiedDuringIndexing: util.HashMap[Scope, Int] = new util.HashMap[Scope, Int]()
  protected val compilationAffectedScopes: util.Set[Scope]       = ContainerUtil.newConcurrentSet[Scope]()
  protected var indexingPhases: Int                              = 0

  protected def scopeForSourceContentFile(vfile: VirtualFile): Set[Scope]
  protected def moduleScopes(m: Module): Set[Scope]
  protected def scopeToSearchScope(scope: Scope): GlobalSearchScope

  private[compilerReferences] def markScopeUpToDate(scope: Scope): Unit = compilationAffectedScopes.add(scope)

  private[compilerReferences] def markProjectAsOutdated(): Unit = lock.locked(project.sourceModules.foreach(markModuleAsDirty))

  private[compilerReferences] def reset(): Unit = lock.locked {
    markProjectAsOutdated()
    modifiedDuringIndexing.clear()
    compilationAffectedScopes.clear()
    indexingPhases = 0
  }

  override def after(events: util.List[_ <: VFileEvent]): Unit = events.forEach {
    case e @ (_: VFileCreateEvent | _: VFileMoveEvent | _: VFileCopyEvent) => onFileChange(e.getFile)
    case pce: VFilePropertyChangeEvent =>
      val propertyName = pce.getPropertyName
      if (propertyName == VirtualFile.PROP_NAME || propertyName == VirtualFile.PROP_SYMLINK_TARGET)
        onFileChange(pce.getFile)
    case _ => ()
  }

  override def before(events: util.List[_ <: VFileEvent]): Unit = events.forEach {
    case e @ (_: VFileDeleteEvent | _: VFileMoveEvent | _: VFileContentChangeEvent) => onFileChange(e.getFile)
    case pce: VFilePropertyChangeEvent =>
      val propertyName = pce.getPropertyName
      if (propertyName == VirtualFile.PROP_NAME || propertyName == VirtualFile.PROP_SYMLINK_TARGET) {
        val file = pce.getFile
        val module =
          ProjectFileIndex.getInstance(project).getModuleForFile(file)
            .toOption
            .filter(_.isSourceModule)

        module.foreach(markModuleAsDirty)
      }
    case _ => ()
  }

  private[this] def onFileChange(@Nullable vfile: VirtualFile): Unit =
    vfile.toOption.foreach(f => addToDirtyScopes(scopeForSourceContentFile(f)))

  protected def markModuleAsDirty(m: Module): Unit = lock.locked(addToDirtyScopes(moduleScopes(m)))

  protected def addToDirtyScopes(scopes: Set[Scope]): Unit = lock.locked {
    if (indexingPhases != 0) {
      scopes.foreach(scope =>
        modifiedDuringIndexing.merge(scope, indexingPhases, Math.max(_, _))
      )
    }
    else vfsChangedScopes.addAll(scopes.asJava)
  }

  private[compilerReferences] def indexingStarted(): Unit = lock.locked(indexingPhases += 1)

  private[compilerReferences] def indexingFinished(): Unit = lock.locked {
    indexingPhases -= 1
    vfsChangedScopes.removeAll(compilationAffectedScopes)
    compilationAffectedScopes.clear()

    val iter = modifiedDuringIndexing.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      entry.setValue(entry.getValue - indexingPhases)

      if (entry.getValue == 0) iter.remove()
      else                     addToDirtyScopes(Set(entry.getKey))
    }
  }

  private[compilerReferences] def installVFSListener(): Unit =
    project.getMessageBus.connect(project.unloadAwareDisposable).subscribe(VirtualFileManager.VFS_CHANGES, this)

  def dirtyScope: GlobalSearchScope = inReadAction {
    lock.locked {
      if (indexingPhases != 0) GlobalSearchScope.allScope(project)
      else if (!project.isDisposed) {
        CachedValuesManager
          .getManager(project)
          .getCachedValue(
            this,
            () =>
              CachedValueProvider.Result
                .create(calcDirtyScope(), PsiModificationTracker.MODIFICATION_COUNT, VirtualFileManager.getInstance(), modificationTracker)
          )
      } else GlobalSearchScope.EMPTY_SCOPE
    }
  }

  private[this] def calcDirtyScope(): GlobalSearchScope =
    if (dirtyScopes.isEmpty) GlobalSearchScope.EMPTY_SCOPE
    else                     GlobalSearchScope.union(dirtyScopes.map(scopeToSearchScope).asJavaCollection)

  def contains(file: VirtualFile): Boolean = dirtyScope.contains(file)

  def dirtyScopes: Set[Scope] = {
    val dirty = Set.newBuilder[Scope]
    dirty ++= vfsChangedScopes.asScala

    fileDocManager.getUnsavedDocuments.foreach { doc =>
      for {
        file  <- fileDocManager.getFile(doc).toOption
        scope <- scopeForSourceContentFile(file)
      } dirty += scope
    }

    psiDocManager.getUncommittedDocuments.foreach { doc =>
      for {
        pfile <- psiDocManager.getPsiFile(doc).toOption
        vfile <- pfile.getVirtualFile.toOption
        scope <- scopeForSourceContentFile(vfile)
      } dirty += scope
    }

    dirty.result()
  }
}

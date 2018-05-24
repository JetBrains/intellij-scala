package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.{File, IOException}
import java.util

import com.intellij.compiler.backwardRefs.{CompilerHierarchySearchType, CompilerReferenceReader, SearchId}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile, VirtualFileWithId}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.Queue
import com.intellij.util.indexing.StorageException
import com.intellij.util.indexing.ValueContainer.ContainerAction
import gnu.trove.{THashSet, TIntHashSet}
import org.jetbrains.jps.backwardRefs.CompilerRef

import scala.annotation.tailrec

private[findUsages] class ScalaCompilerReferenceReader private[compilerReferences] (
  buildDir: File
) extends CompilerReferenceReader[ScalaCompilerReferenceIndex](
      buildDir,
      new ScalaCompilerReferenceIndex(buildDir, true)
    ) {

  private def rethrowStorageExceptionIn[T](body: => T): T =
    try body
    catch { case e: StorageException => throw new RuntimeException(e) }

  private def findFileByEnumeratorId(id: Int): Option[VirtualFile] = {
    val path = myIndex.getFilePathEnumerator.valueOf(id)

    try Option(VfsUtil.findFileByIoFile(new File(path), false))
    catch { case e: IOException => throw new RuntimeException(e) }
  }

  def usagesOf(ref: CompilerRef): Set[LinesWithUsagesInFile] =
    rethrowStorageExceptionIn {
      val usages = Set.newBuilder[LinesWithUsagesInFile]
      
      searchInBackwardUsagesIndex(ref) {
        case (fileId, lines) =>
          val file = findFileByEnumeratorId(fileId)
          file.foreach(usages += LinesWithUsagesInFile(_, lines))
          true
      }
      
      usages.result()
    }

  override def findReferentFileIds(ref: CompilerRef, checkBaseClassAmbiguity: Boolean): TIntHashSet =
    rethrowStorageExceptionIn { 
      val referentFiles = new TIntHashSet()
      
      searchInBackwardUsagesIndex(ref) {
        case (fileId, _) =>
          findFileByEnumeratorId(fileId).foreach(f => referentFiles.add(f.asInstanceOf[VirtualFileWithId].getId))
          true
      }
      
      referentFiles
    }

  private[this] def searchInBackwardUsagesIndex(
    ref: CompilerRef
  )(action: ContainerAction[Seq[Int]]): Unit = {
    val hierarchy = ref match {
      case classRef: CompilerRef.CompilerClassHierarchyElementDef => Array(classRef)
      case member: CompilerRef.CompilerMember =>
        getHierarchy(member.getOwner, checkBaseClassAmbiguity = true, includeAnonymous = true, -1)
          .map(identity) // scala arrays are invariant
      case _ => throw new IllegalArgumentException("Should never happen.")
    }

    hierarchy.foreach { owner =>
      val overridden = ref.`override`(owner.getName)
      myIndex.get(ScalaCompilerIndices.backwardUsages).getData(overridden).forEach(action)
    }
  }

  override def getHierarchy(
    hierarchyElement: CompilerRef.CompilerClassHierarchyElementDef,
    checkBaseClassAmbiguity: Boolean,
    includeAnonymous: Boolean,
    interruptNumber: Int
  ): Array[CompilerRef.CompilerClassHierarchyElementDef] = rethrowStorageExceptionIn {
    val res = new THashSet[CompilerRef.CompilerClassHierarchyElementDef]()
    val queue = new Queue[CompilerRef.CompilerClassHierarchyElementDef](10)

    @tailrec
    def drain(q: Queue[CompilerRef.CompilerClassHierarchyElementDef]): Unit = if (!queue.isEmpty) {
      if (interruptNumber == -1 || res.size() <= interruptNumber) {
        val currentClass = q.pullFirst()
        if (res.add(currentClass)) {

          if (res.size() % 100 == 0) {
            ProgressManager.checkCanceled()
          }

          myIndex.get(ScalaCompilerIndices.backwardHierarchy).getData(currentClass).forEach {
            case (_, child) => child match {
                case anon: CompilerRef.CompilerAnonymousClassDef if includeAnonymous => queue.addLast(anon)
                case aClass: CompilerRef.CompilerClassHierarchyElementDef            => queue.addLast(aClass)
                case _                                                               => ()
              }
              true
          }
        }
        drain(q)
      }
    }

    queue.addLast(hierarchyElement)
    drain(queue)
    res.toArray(CompilerRef.CompilerClassHierarchyElementDef.EMPTY_ARRAY)
  }

  override def getAnonymousCount(
    compilerClassHierarchyElementDef: CompilerRef.CompilerClassHierarchyElementDef,
    b: Boolean
  ): Integer = 0

  override def getOccurrenceCount(lightRef: CompilerRef): Int = 0

  override def getDirectInheritors(
    lightRef: CompilerRef,
    globalSearchScope: GlobalSearchScope,
    globalSearchScope1: GlobalSearchScope,
    fileType: FileType,
    compilerHierarchySearchType: CompilerHierarchySearchType
  ): util.Map[VirtualFile, Array[SearchId]] = null
}

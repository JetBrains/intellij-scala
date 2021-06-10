package org.jetbrains.plugins.scala.findUsages.compilerReferences.indices

import java.io.File
import java.io.IOException
import java.util
import com.intellij.compiler.backwardRefs.CompilerHierarchySearchType
import com.intellij.compiler.backwardRefs.CompilerReferenceReader
import com.intellij.compiler.backwardRefs.SearchId
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.{CompactVirtualFileSet, VfsUtil, VirtualFile, VirtualFileWithId}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.Queue
import com.intellij.util.indexing.StorageException
import com.intellij.util.indexing.ValueContainer.ContainerAction
import gnu.trove.THashSet
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.plugins.scala.findUsages.compilerReferences.UsagesInFile

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

  def usagesOf(ref: CompilerRef): Set[UsagesInFile] =
    rethrowStorageExceptionIn {
      val usages = Set.newBuilder[UsagesInFile]

      searchInBackwardUsagesIndex(ref) {
        case (fileId, lines) =>
          val file = findFileByEnumeratorId(fileId)
          file.foreach(usages += UsagesInFile(_, lines.toSeq))
          true
      }

      usages.result()
    }

  def anonymousSAMImplementations(classRef: CompilerRef): Set[UsagesInFile] =
    rethrowStorageExceptionIn {
      val usages = Set.newBuilder[UsagesInFile]

      myIndex.get(ScalaCompilerIndices.backwardHierarchy).getData(classRef).forEach {
        case (fileId, inheritors) =>
          val lines = inheritors.iterator.collect { case funExpr: ScFunExprCompilerRef => funExpr.line }.toSeq
          val file  = findFileByEnumeratorId(fileId)
          file.foreach(usages += UsagesInFile(_, lines))
          true
      }

      usages.result()
    }

  override def findReferentFileIds(ref: CompilerRef, checkBaseClassAmbiguity: Boolean): util.Set[VirtualFile] =
    rethrowStorageExceptionIn {
      val referentFiles = new CompactVirtualFileSet

      searchInBackwardUsagesIndex(ref) {
        case (fileId, _) =>
          findFileByEnumeratorId(fileId).foreach(f => referentFiles.add(f))
          true
      }

      referentFiles
    }

  private[this] def searchInBackwardUsagesIndex(
    ref:    CompilerRef
  )(action: ContainerAction[collection.Seq[Int]]): Unit = {
    val hierarchy = ref match {
      case classRef: CompilerRef.CompilerClassHierarchyElementDef => Array(classRef)
      case member: CompilerRef.CompilerMember =>
        getHierarchy(member.getOwner, checkBaseClassAmbiguity = true, includeAnonymous = true, -1)
          .map(identity)
      case _ => throw new IllegalArgumentException(s"Should never happen. $ref")
    }

    hierarchy.foreach { owner =>
      val overridden = ref.`override`(owner.getName)
      myIndex.get(ScalaCompilerIndices.backwardUsages).getData(overridden).forEach(action)
    }
  }

  override def getHierarchy(
    hierarchyElement:        CompilerRef.CompilerClassHierarchyElementDef,
    checkBaseClassAmbiguity: Boolean,
    includeAnonymous:        Boolean,
    interruptNumber:         Int
  ): Array[CompilerRef.CompilerClassHierarchyElementDef] = rethrowStorageExceptionIn {
    val res   = new THashSet[CompilerRef.CompilerClassHierarchyElementDef]()
    val queue = new util.ArrayDeque[CompilerRef.CompilerClassHierarchyElementDef](10)

    @tailrec
    def drain(q: util.ArrayDeque[CompilerRef.CompilerClassHierarchyElementDef]): Unit = if (!queue.isEmpty) {
      if (interruptNumber == -1 || res.size() <= interruptNumber) {
        val currentClass = q.poll()
        if (res.add(currentClass)) {

          if (res.size() % 100 == 0) {
            ProgressManager.checkCanceled()
          }

          myIndex.get(ScalaCompilerIndices.backwardHierarchy).getData(currentClass).forEach {
            case (_, children) =>
              children.foreach {
                case anon: CompilerRef.CompilerAnonymousClassDef if includeAnonymous => res.add(anon)
                case _: ScFunExprCompilerRef                                         => ()
                case aClass: CompilerRef.CompilerClassHierarchyElementDef            => queue.addLast(aClass)
                case other =>
                  throw new AssertionError(s"Expected class ref in hierarchy index, but got $other.")
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
    b:                                Boolean
  ): Integer = 0

  override def getOccurrenceCount(lightRef: CompilerRef): Int = 0

  override def getDirectInheritors(
    lightRef:                    CompilerRef,
    globalSearchScope:           GlobalSearchScope,
    globalSearchScope1:          GlobalSearchScope,
    fileType:                    FileType,
    compilerHierarchySearchType: CompilerHierarchySearchType
  ): util.Map[VirtualFile, Array[SearchId]] = null

  override def findFileIdsWithImplicitToString(compilerRef: CompilerRef): util.Set[VirtualFile] = null
}

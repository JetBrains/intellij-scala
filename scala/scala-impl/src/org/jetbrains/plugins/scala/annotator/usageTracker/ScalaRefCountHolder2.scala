package org.jetbrains.plugins.scala.annotator.usageTracker

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzer, impl}
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{JavaResolveResult, PsiAnchor, PsiClass, PsiDocumentManager, PsiElement, PsiFile, PsiImportStatementBase, PsiJavaReference, PsiMethod, PsiNamedElement, PsiReference}
import com.intellij.reference.SoftReference
import com.intellij.util.containers.{HashingStrategy, MultiMap}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportUsed, ValueUsed}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import java.lang.ref.Reference
import java.util
import java.util.{Map, Set}

class ScalaRefCountHolder private (
  private val myFile: PsiFile,
  private val myLocalRefsMap: MultiMap[PsiElement, PsiReference],
  private val myDclsUsedMap: Set[PsiAnchor],
  private val myImportStatements: Map[PsiReference, PsiImportStatementBase]
) {
  @volatile private var ready = false

  def registerImportStatement(ref: PsiReference, importStatement: PsiImportStatementBase): Unit = {
    myImportStatements.put(ref, importStatement)
  }

  private def registerLocalRef(ref: PsiReference, refElement: PsiElement): Unit = {
    val element = ref.getElement
    if (refElement.is[PsiMethod] && PsiTreeUtil.isAncestor(refElement, element, true)) return // filter self-recursive calls
    if (refElement.is[PsiClass]) if (PsiTreeUtil.isAncestor(refElement, element, true)) return // filter inner use of itself
    myLocalRefsMap.putValue(refElement, ref)
  }


  def registerValueUsed(used: ValueUsed): Unit = {
    //    myValueUsed.add(used)
  }

  def usageFound(used: ImportUsed): Boolean = {
    false
    //    assertReady()
    //    myImportUsed.contains(used)
  }

  def isValueWriteUsed(element: PsiNamedElement): Boolean = false

  def isValueReadUsed(element: PsiNamedElement): Boolean = false

  def analyze(analyze: Runnable, file: PsiFile): Boolean = {
    true
    //    val currentCount = currentModCount
    //    val lastCount = lastReadyModCount.get()
    //
    //    if (!isReady) {
    //      cleanIfDirty(file)
    //    }
    //
    //        analyze.run()
    //
    //    //don't cancel next passes if holder was updated concurrently
    //    lastReadyModCount.compareAndSet(lastCount, currentCount)
    //
    //    isReady
  }


  def runIfUnusedReferencesInfoIsAlreadyRetrievedOrSkip(analyze: () => Unit): Boolean = {
    true
    //    if (isReady) {
    //      analyze()
    //      true
    //    }
    //    else false
  }

  private def removeInvalidRefs(): ScalaRefCountHolder = {
    assert(ready)
    var changed = false
    val newLocalRefsMap: MultiMap[PsiElement, PsiReference] = MultiMap.createConcurrentSet()

    myLocalRefsMap.entrySet().forEach { entry =>
      val element = entry.getKey
      entry.getValue.forEach { ref =>
        if (ref.getElement.isValid) {
          newLocalRefsMap.putValue(element, ref)
        } else {
          changed = true
        }
      }
    }

    val newDclsUsedMap: util.Set[PsiAnchor] = ConcurrentCollectionFactory.createConcurrentSet(HashingStrategy.canonical())
    myDclsUsedMap.forEach { psiAnchor =>
      if (psiAnchor.retrieve() != null) {
        newDclsUsedMap.add(psiAnchor)
      } else {
        changed = true
      }
    }

    val newImportStatements: Map[PsiReference, PsiImportStatementBase] = ConcurrentCollectionFactory.createConcurrentMap()
    myImportStatements.entrySet().forEach { entry =>
      val key = entry.getKey
      val value = entry.getValue
      if (value.isValid && key.getElement.isValid) {
        newImportStatements.put(key, value)
      } else {
        changed = true
      }
    }

    if (changed) new ScalaRefCountHolder(myFile, newLocalRefsMap, newDclsUsedMap, newImportStatements)
    else this
  }
}

object ScalaRefCountHolder {

  private val ScalaRefCountHolderKey: Key[Reference[ScalaRefCountHolder]] = Key.create("ScalaRefCountHolderKey")

  def get(file: PsiFile): ScalaRefCountHolder = {
    val project = file.getProject
    val document = PsiDocumentManager.getInstance(project).getDocument(file)
    var dirtyScope = if (document == null) null
    else DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL)
    if (dirtyScope == null) dirtyScope = file.getTextRange

    val ref = file.getUserData(ScalaRefCountHolderKey)
    val storedHolder = SoftReference.dereference(ref)
    val wholeFile = dirtyScope.equals(file.getTextRange)
    if (storedHolder != null && !wholeFile) {
      return null
    }
    if (storedHolder == null || wholeFile) {
      new ScalaRefCountHolder(
        file,
        MultiMap.createConcurrentSet(),
        ConcurrentCollectionFactory.createConcurrentSet(HashingStrategy.canonical()),
        ConcurrentCollectionFactory.createConcurrentMap()
      )
    } else {
      storedHolder.removeInvalidRefs()
    }
  }

  def findDirtyScope(file: PsiFile): Option[Option[TextRange]] = {
    val project = file.getProject
    PsiDocumentManager.getInstance(project).getDocument(file) match {
      case null => None
      case document =>
        DaemonCodeAnalyzer.getInstance(project) match {
          case analyzerImpl: impl.DaemonCodeAnalyzerImpl =>
            Some(Option(analyzerImpl.getFileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL)))
          case _ => None
        }
    }
  }
}
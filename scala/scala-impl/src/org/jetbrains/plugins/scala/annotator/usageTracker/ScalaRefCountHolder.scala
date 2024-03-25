package org.jetbrains.plugins.scala.annotator.usageTracker

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzer, impl}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._

import java.lang.ref.{Reference, SoftReference}
import java.util.concurrent.atomic.AtomicLong
import java.{util => ju}
import scala.jdk.CollectionConverters.CollectionHasAsScala

final class ScalaRefCountHolder private (file: PsiFile) {

  import ScalaRefCountHolder._

  private val lastReadyModCount = new AtomicLong(-1)

  private def isReady: Boolean = {
    lastReadyModCount.get() == currentModCount
  }

  private def assertReady(): Unit = {
    Log.assertTrue(isReady)
  }

  private def currentModCount: Long = CachesUtil.fileModCount(file)

  private val myImportUsed = ContainerUtil.newConcurrentSet[ImportUsed]
  private val myValueUsed = ContainerUtil.newConcurrentSet[ValueUsed]

  def registerImportUsed(used: ImportUsed): Unit = {
    myImportUsed.add(used)
  }

  def registerValueUsed(used: ValueUsed): Unit = {
    myValueUsed.add(used)
  }

  def usageFound(used: ImportUsed): Boolean = {
    assertReady()
    myImportUsed.contains(used)
  }

  def getWriteReferences(element: PsiNamedElement): Seq[SmartPsiElementPointer[PsiElement]] =
    myValueUsed.asScala.collect {
      case w: WriteValueUsed if w.declaration.getElement != null && w.declaration.getElement == element => w
    }.map(_.reference).toSeq

  def getReadReferences(element: PsiNamedElement): Seq[SmartPsiElementPointer[PsiElement]] =
    myValueUsed.asScala.collect {
      case r: ReadValueUsed if r.declaration.getElement != null && r.declaration.getElement == element => r
    }.map(_.reference).toSeq

  def isValueWriteUsed(element: PsiNamedElement): Boolean = {
    assertReady()
    myValueUsed.asScala.collect {
      case w: WriteValueUsed if w.declaration.getElement != null => w
    }.exists(_.declaration.getElement == element)
  }

  def isValueReadUsed(element: PsiNamedElement): Boolean = {
    assertReady()
    myValueUsed.asScala.collect {
      case r: ReadValueUsed if r.declaration.getElement != null => r
    }.exists(_.declaration.getElement == element)
  }

  def analyze(analyze: Runnable, file: PsiFile): Boolean = {

    val currentCount = currentModCount
    val lastCount = lastReadyModCount.get()

    if (!isReady) {
      cleanIfDirty(file)
    }

    analyze.run()

    //don't cancel next passes if holder was updated concurrently
    lastReadyModCount.compareAndSet(lastCount, currentCount)

    isReady
  }

  def isNew: Boolean = lastReadyModCount.get() == -1


  def runIfUnusedReferencesInfoIsAlreadyRetrievedOrSkip(analyze: () => Unit): Boolean = {
    if (isReady) {
      analyze()
      true
    }
    else false
  }

  private def cleanIfDirty(file: PsiFile): Unit = {
    val dirtyScope = findDirtyScope(file)
    val defaultRange = Some(file.getTextRange)
    dirtyScope.getOrElse(defaultRange) match {
      case `defaultRange` =>
        myImportUsed.clear()
        myValueUsed.clear()
      case Some(_) =>
        clear(myImportUsed)(_.isValid)
        clear(myValueUsed)(_.isValid)
      case _ =>
    }
  }
}

object ScalaRefCountHolder {

  private val Log = Logger.getInstance(getClass)

  private val key: Key[Reference[ScalaRefCountHolder]] = Key.create("scala.ref.count.holder")

  def apply(element: PsiNamedElement): ScalaRefCountHolder =
    getInstance(element.getContainingFile)

  def getInstance(file: PsiFile): ScalaRefCountHolder =
    key.synchronized {
      val ref = file.getUserData(key)
      if (ref != null) {
        val stored = ref.get()
        if (stored != null)
          return stored
      }

      val refCountHolder = new ScalaRefCountHolder(file)
      file.putUserData(key, new SoftReference(refCountHolder))

      refCountHolder
    }

  def findDirtyScope(file: PsiFile): Option[Option[TextRange]] = {
    val project = file.getProject
    PsiDocumentManager.getInstance(project).getDocument(file) match {
      case null => None
      case document =>
        DaemonCodeAnalyzer.getInstance(project) match {
          case analyzerImpl: impl.DaemonCodeAnalyzerImpl =>
            Some(Option(analyzerImpl.getFileStatusMap.getFileDirtyScope(document, file, Pass.UPDATE_ALL)))
          case _ => None
        }
    }
  }

  private def clear[T](used: ju.Set[T])
                      (isValid: T => Boolean): Unit = used.synchronized {
    val valuesIterator = used.iterator
    while (valuesIterator.hasNext) {
      if (!isValid(valuesIterator.next)) {
        valuesIterator.remove()
      }
    }
  }
}

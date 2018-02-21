package org.jetbrains.plugins.scala.annotator.usageTracker

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi._
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder.WeakKeyTimestampedValueMap
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative

import scala.collection.mutable
import scala.concurrent.duration.{Duration, DurationLong}

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */

/**
 * See com.intellij.codeInsight.daemon.impl.RefCountHolder
 */
class ScalaRefCountHolder private () {
  private final val myState: AtomicReference[Integer] = new AtomicReference[Integer](State.VIRGIN)
  private object State {val VIRGIN = 0; val WRITE = 1; val READY = 2; val READ = 3;}
  private val myImportUsed = ContainerUtil.newConcurrentSet[ImportUsed]()
  private val myValueUsed = ContainerUtil.newConcurrentSet[ValueUsed]()

  private def clear() {
    assertIsAnalyzing()
    myImportUsed.clear()
    myValueUsed.clear()
  }

  def registerImportUsed(used: ImportUsed) {
    myImportUsed.add(used)
  }

  def registerValueUsed(used: ValueUsed) {
    myValueUsed.add(used)
  }

  def noUsagesFound(used: ImportUsed): Boolean = {
    assertIsRetrieving()
    !myImportUsed.contains(used)
  }

  def isValueWriteUsed(e: PsiNamedElement): Boolean = {
    assertIsRetrieving()
    myValueUsed.contains(WriteValueUsed(e))
  }

  def isValueReadUsed(e: PsiNamedElement): Boolean = {
    assertIsRetrieving()
    myValueUsed.contains(ReadValueUsed(e))
  }

  def isValueUsed(e: PsiNamedElement): Boolean = {
    assertIsRetrieving()
    isValueReadUsed(e) || isValueWriteUsed(e)
  }

  private def removeInvalidRefs() {
    assertIsAnalyzing()
    myImportUsed synchronized {
      val iterator: java.util.Iterator[ImportUsed] = myImportUsed.iterator
      while (iterator.hasNext) {
        val ref: ImportUsed = iterator.next
        if (!ref.e.isValid) {
          iterator.remove()
        }
      }
    }
    myValueUsed synchronized {
      val valuesIterator: java.util.Iterator[ValueUsed] = myValueUsed.iterator()
      while (valuesIterator.hasNext) {
        val ref: ValueUsed = valuesIterator.next
        if (!ref.isValid) valuesIterator.remove()
      }
    }
  }

  def analyze(analyze: Runnable, dirtyScope: TextRange, file: PsiFile): Boolean = {
    myState.compareAndSet(State.READY, State.VIRGIN)
    if (!myState.compareAndSet(State.VIRGIN, State.WRITE)) return false
    try {
      if (dirtyScope != null) {
        if (dirtyScope.equals(file.getTextRange)) {
          clear()
        }
        else {
          removeInvalidRefs()
        }
      }
      analyze.run()
    }
    finally {
      val set: Boolean = myState.compareAndSet(State.WRITE, State.READY)
      assert(set, myState.get)
    }
    true
  }


  def retrieveUnusedReferencesInfo(analyze: () => Unit): Boolean = {
    if (!myState.compareAndSet(State.READY, State.READ)) {
      return false
    }
    try {
      analyze()
    }
    finally {
      val set: Boolean = myState.compareAndSet(State.READ, State.READY)
      assert(set, myState.get)
    }
    true
  }


  private def assertIsAnalyzing() {
    assert(myState.get == State.WRITE, myState.get)
  }


  private def assertIsRetrieving() {
    assert(myState.get == State.READ, myState.get)
  }
}

object ScalaRefCountHolder {

  def getDirtyScope(file: PsiFile): Option[TextRange] = {
    val project = file.getProject
    val document: Document = PsiDocumentManager.getInstance(project).getDocument(file)

    if (document == null) Some(file.getTextRange)
    else DaemonCodeAnalyzer.getInstance(project) match {
      case analyzerImpl: DaemonCodeAnalyzerImpl =>
        val fileStatusMap = analyzerImpl.getFileStatusMap
        Option(fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL))
      case _ => Some(file.getTextRange)
    }
  }

  def getInstance(file: PsiFile): ScalaRefCountHolder = {
    val myFile = Option(ScalaLanguageDerivative.getScalaFileOnDerivative(file)).getOrElse(file)
    val component = myFile.getProject.getComponent(classOf[ScalaRefCountHolderComponent])
    component.getOrCreate(file, new ScalaRefCountHolder)
  }

  class WeakKeyTimestampedValueMap[K, V](minimumSize: Int, storageTime: Duration) {
    private case class Timestamped(value: V, var timestamp: Long = -1, var accessId: Long = -1)

    private[this] val accessId = new AtomicLong(0)
    private[this] val innerMap: mutable.WeakHashMap[K, Timestamped] = mutable.WeakHashMap()

    def getOrCreate(k: K, v: => V): V = innerMap.synchronized {
      val value = innerMap.getOrElseUpdate(k, Timestamped(v))
      value.timestamp = System.currentTimeMillis()
      value.accessId = accessId.incrementAndGet()
      value.value
    }

    def removeStaleEntries(): Unit = innerMap.synchronized {
      val currentSize = innerMap.size
      if (currentSize <= minimumSize) return

      val currentTime = System.currentTimeMillis()
      val sorted = innerMap.toArray.sortBy(_._2.accessId)

      val possiblyStale = sorted.take(currentSize - minimumSize)
      possiblyStale.foreach {
        case (k, v) if currentTime - v.timestamp > storageTime.toMillis =>
          innerMap.remove(k)
        case _ =>
      }
    }
  }
}

class ScalaRefCountHolderComponent(project: Project) extends AbstractProjectComponent(project) {
  private val autoCleaningMap: Ref[WeakKeyTimestampedValueMap[PsiFile, ScalaRefCountHolder]] = Ref.create()

  private val numberOfFilesToKeep = 3
  private val otherFilesStorageTime = 5.minutes
  private val cleanupInterval = 1.minute

  override def projectOpened(): Unit = {
    autoCleaningMap.set(new WeakKeyTimestampedValueMap(numberOfFilesToKeep, otherFilesStorageTime))

    JobScheduler.getScheduler.scheduleWithFixedDelay (
      cleanupTask(autoCleaningMap), cleanupInterval.toMillis, cleanupInterval.toMillis, TimeUnit.MILLISECONDS
    )
  }

  override def projectClosed(): Unit = {
    autoCleaningMap.set(null)
  }

  private def cleanupTask(map: Ref[WeakKeyTimestampedValueMap[PsiFile, ScalaRefCountHolder]]): Runnable = () => {
    map.get().removeStaleEntries()
  }

  def getOrCreate(file: PsiFile, holder: => ScalaRefCountHolder): ScalaRefCountHolder =
    Option(autoCleaningMap.get())
      .map(_.getOrCreate(file, holder))
      .getOrElse(holder)
}
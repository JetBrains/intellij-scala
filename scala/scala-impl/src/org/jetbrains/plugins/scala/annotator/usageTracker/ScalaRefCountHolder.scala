package org.jetbrains.plugins.scala.annotator.usageTracker

import java.util
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{LowMemoryWatcher, Ref, TextRange}
import com.intellij.psi._
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.hash.LinkedHashMap
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder.TimestampedValueMap
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative

import scala.collection.JavaConverters.asScalaIteratorConverter
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
        if (!ref.element.isValid) {
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

  private case class Timestamped[V](value: V, var timestamp: Long = -1)

  class TimestampedValueMap[K, V](minimumSize: Int, maximumSize: Int, storageTime: Duration) {

    private[this] val innerMap: util.Map[K, Timestamped[V]] =
      new LinkedHashMap[K, Timestamped[V]](maximumSize, true) {

        override def removeEldestEntry(eldest: util.Map.Entry[K, Timestamped[V]]): Boolean = size() > maximumSize

        override def doRemoveEldestEntry(): Unit = innerMap.synchronized {
          super.doRemoveEldestEntry()
        }
      }

    def getOrCreate(k: K, v: => V): V = innerMap.synchronized {
      val value = {
        val cached = innerMap.get(k)
        if (cached != null) cached
        else {
          val newValue = Timestamped(v)
          innerMap.put(k, newValue)
          newValue
        }
      }

      value.timestamp = System.currentTimeMillis()
      value.value
    }

    def removeStaleEntries(): Unit = innerMap.synchronized {
      val currentSize = innerMap.size
      if (currentSize <= minimumSize) return

      val currentTime = System.currentTimeMillis()
      val iterator = innerMap.entrySet().iterator()

      val possiblyStale = iterator.asScala.take(currentSize - minimumSize)

      possiblyStale.foreach {
        case e if currentTime - e.getValue.timestamp > storageTime.toMillis =>
          innerMap.remove(e.getKey)
        case _ =>
      }
    }
  }
}

class ScalaRefCountHolderComponent(project: Project) extends ProjectComponent {
  private val autoCleaningMap: Ref[TimestampedValueMap[String, ScalaRefCountHolder]] = Ref.create()

  private val numberOfFilesToKeep = 3
  private val maxNumberOfFiles = 20
  private val otherFilesStorageTime = 5.minutes
  private val cleanupInterval = 1.minute

  override def projectOpened(): Unit = {
    autoCleaningMap.set(new TimestampedValueMap(numberOfFilesToKeep, maxNumberOfFiles, otherFilesStorageTime))

    JobScheduler.getScheduler.scheduleWithFixedDelay (
      cleanupTask(autoCleaningMap), cleanupInterval.toMillis, cleanupInterval.toMillis, TimeUnit.MILLISECONDS
    )

    LowMemoryWatcher.register(cleanupTask(autoCleaningMap), project)
  }

  override def projectClosed(): Unit = {
    autoCleaningMap.set(null)
  }

  private def cleanupTask(map: Ref[TimestampedValueMap[String, ScalaRefCountHolder]]): Runnable = () => {
    map.get().removeStaleEntries()
  }

  def getOrCreate(file: PsiFile, holder: => ScalaRefCountHolder): ScalaRefCountHolder = {
    val key = file.getName + file.hashCode()
    Option(autoCleaningMap.get())
      .map(_.getOrCreate(key, holder))
      .getOrElse(holder)
  }
}
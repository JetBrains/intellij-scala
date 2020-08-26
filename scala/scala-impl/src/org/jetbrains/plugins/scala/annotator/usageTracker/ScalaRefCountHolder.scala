package org.jetbrains.plugins.scala
package annotator
package usageTracker

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicLong
import java.{util => ju}

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzer, impl}
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.{LowMemoryWatcher, Ref, TextRange}
import com.intellij.psi._
import com.intellij.util.containers.{ContainerUtil, hash}
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */
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

  def isValueWriteUsed(element: PsiNamedElement): Boolean = isValueUsed {
    WriteValueUsed(element)
  }

  def isValueReadUsed(element: PsiNamedElement): Boolean = isValueUsed {
    ReadValueUsed(element)
  }

  private def isValueUsed(used: ValueUsed): Boolean = {
    assertReady()
    myValueUsed.contains(used)
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


  def retrieveUnusedReferencesInfo(analyze: () => Unit): Boolean = {
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

  def apply(element: PsiNamedElement): ScalaRefCountHolder =
    getInstance(element.getContainingFile)

  def getInstance(file: PsiFile): ScalaRefCountHolder = file.getProject
    .getService(classOf[ScalaRefCountHolderService])
    .getOrCreate(
      file.getName + file.hashCode,
      new ScalaRefCountHolder(file)
    )

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

final class ScalaRefCountHolderService(project: Project) extends Disposable {

  import ScalaRefCountHolderService._

  private val autoCleaningMap = Ref.create[TimestampedValueMap[String, ScalaRefCountHolder]]

  private var myCleanUpFuture: ScheduledFuture[_] = _

  private def init(): Unit = {
    autoCleaningMap.set(new TimestampedValueMap())

    myCleanUpFuture = JobScheduler.getScheduler.scheduleWithFixedDelay(
      cleanupTask(autoCleaningMap),
      CleanupDelay,
      CleanupDelay,
      ju.concurrent.TimeUnit.MILLISECONDS
    )

    LowMemoryWatcher.register(cleanupTask(autoCleaningMap), project.unloadAwareDisposable)
  }

  override def dispose(): Unit = {
    if (myCleanUpFuture != null) {
      myCleanUpFuture.cancel(false)
      myCleanUpFuture = null
    }
    autoCleaningMap.set(null)
  }

  private def cleanupTask(map: Ref[TimestampedValueMap[String, ScalaRefCountHolder]]): Runnable = () => {
    map.get().removeStaleEntries()
  }

  def getOrCreate(key: String, holder: => ScalaRefCountHolder): ScalaRefCountHolder = {
    autoCleaningMap.get match {
      case null => holder
      case map => map.getOrCreate(key, holder)
    }
  }
}

object ScalaRefCountHolderService {

  def getInstance(project: Project): ScalaRefCountHolderService =
    project.getService(classOf[ScalaRefCountHolderService])

  import concurrent.duration._

  private val CleanupDelay = 1.minute.toMillis

  final private[usageTracker] class TimestampedValueMap[K, V](minimumSize: Int = 3,
                                                              maximumSize: Int = 20,
                                                              storageTime: Long = 5.minutes.toMillis) {

    private[this] case class Timestamped(value: V, var timestamp: Long = -1)

    private[this] val innerMap = new hash.LinkedHashMap[K, Timestamped](maximumSize, true) {

      override def removeEldestEntry(eldest: ju.Map.Entry[K, Timestamped]): Boolean = size() > maximumSize

      override def doRemoveEldestEntry(): Unit = this.synchronized {
        super.doRemoveEldestEntry()
      }
    }

    def getOrCreate(key: K, value: => V): V = innerMap.synchronized {
      val timestamped = innerMap.get(key) match {
        case null =>
          val newValue = Timestamped(value)
          innerMap.put(key, newValue)
          newValue
        case cached => cached
      }

      timestamped.timestamp = System.currentTimeMillis()
      timestamped.value
    }

    def removeStaleEntries(): Unit = innerMap.synchronized {
      innerMap.size - minimumSize match {
        case diff if diff > 0 =>
          val timeDiff = System.currentTimeMillis() - storageTime

          import scala.jdk.CollectionConverters._
          innerMap.entrySet
            .iterator
            .asScala
            .take(diff)
            .filter(_.getValue.timestamp < timeDiff)
            .map(_.getKey)
            .foreach(innerMap.remove)
        case _ =>
      }
    }
  }

  final private class Startup extends StartupActivity {
    override def runActivity(project: Project): Unit =
      getInstance(project).init()
  }

}
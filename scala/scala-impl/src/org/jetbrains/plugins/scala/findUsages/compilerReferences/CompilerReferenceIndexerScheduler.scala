package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util.concurrent.locks.ReentrantLock

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions._

import scala.annotation.tailrec
import scala.collection.mutable

private class CompilerReferenceIndexerScheduler(
  project:              Project,
  expectedIndexVersion: Int
) extends IndexerScheduler {
  private[this] val indexer       = new CompilerReferenceIndexer(project, expectedIndexVersion)
  private[this] val jobQueue      = mutable.Queue.empty[IndexerJob]
  private[this] val lock          = new ReentrantLock()
  private[this] val queueNonEmpty = lock.newCondition()

  start()

  private[this] def start(): Unit =
    executeOnPooledThread(drainQueue())

  @tailrec
  private[this] def drainQueue(): Unit = {
    if (jobQueue.isEmpty) withLock(lock)(queueNonEmpty.awaitUninterruptibly())
    val job = jobQueue.dequeue()
    indexer.process(job)
    drainQueue()
  }

  override def schedule(job: IndexerJob): Unit = {
    jobQueue += job
    withLock(lock)(queueNonEmpty.signal())
  }
}

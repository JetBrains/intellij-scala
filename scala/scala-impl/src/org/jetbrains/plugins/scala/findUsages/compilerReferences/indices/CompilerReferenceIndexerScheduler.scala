package org.jetbrains.plugins.scala.findUsages.compilerReferences
package indices

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{BackgroundTaskQueue, EmptyProgressIndicator}
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.IndexingStage.InvalidateIndex

private[compilerReferences] class CompilerReferenceIndexerScheduler(
  project:              Project,
  expectedIndexVersion: Int
) extends IndexerScheduler {
  import CompilerReferenceIndexerScheduler._

  private[this] val indexer  = new CompilerReferenceIndexer(project, expectedIndexVersion)
  private[this] val jobQueue = new BackgroundTaskQueue(project, ScalaBundle.message("bytecode.indices.progress.title"))

  override def schedule(job: IndexingStage): Unit = synchronized {
    job match {
      case _: InvalidateIndex => jobQueue.clear()
      case _                  => ()
    }

    val task = indexer.toTask(job)
    logger.debug(s"Scheduled indexer job $job.")
    jobQueue.run(task)
  }

  def schedule(@Nls title: String, runnable: () => Unit): Unit = synchronized {
    val t = task(project, title)(_ => runnable())
    jobQueue.run(t)
  }

  override def scheduleAll(jobs: Seq[IndexingStage]): Unit = synchronized(jobs.foreach(schedule))
}

object CompilerReferenceIndexerScheduler {
  private val logger = Logger.getInstance(classOf[CompilerReferenceIndexerScheduler])
}

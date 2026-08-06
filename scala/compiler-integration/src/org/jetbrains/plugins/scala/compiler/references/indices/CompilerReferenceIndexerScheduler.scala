package org.jetbrains.plugins.scala.compiler.references.indices

import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{BackgroundTaskQueue, EmptyProgressIndicator, ProgressIndicator}
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle
import org.jetbrains.plugins.scala.compiler.references.indices.IndexingStage.InvalidateIndex
import org.jetbrains.plugins.scala.compiler.references.task

import scala.annotation.nowarn

private[references] class CompilerReferenceIndexerScheduler(
  project:              Project,
  expectedIndexVersion: Int
) extends IndexerScheduler {
  import CompilerReferenceIndexerScheduler._

  private[this] val indexer  = new CompilerReferenceIndexer(project, expectedIndexVersion)
  // TODO: BackgroundTaskQueue is deprecated. See IJPL-384
  private[this] val jobQueue = new BackgroundTaskQueue(project, CompilerIntegrationBundle.message("bytecode.indices.progress.title")): @nowarn("cat=deprecation")

  override def schedule(job: IndexingStage): Unit = synchronized {
    job match {
      case _: InvalidateIndex => jobQueue.clear()
      case _                  => ()
    }

    val task = indexer.toTask(job)
    logger.debug(s"Scheduled indexer job $job.")
    jobQueue.run(task, ModalityState.nonModal(), progress)
  }

  def schedule(@Nls title: String, runnable: () => Unit): Unit = synchronized {
    val t = task(project, title)(_ => runnable())
    jobQueue.run(t, ModalityState.nonModal(), progress)
  }

  override def scheduleAll(jobs: Seq[IndexingStage]): Unit = synchronized(jobs.foreach(schedule))

  // auto-make doesn't show progress in status bar, we shouldn't do it as well
  // ideally we need to show it on explicit builds, but let's just rely on this setting for now
  private def shouldShowProgress: Boolean =
    !CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE

  private def progress: ProgressIndicator =
    if (shouldShowProgress) null // will be created automatically
    else new EmptyProgressIndicator
}

object CompilerReferenceIndexerScheduler {
  private val logger = Logger.getInstance(classOf[CompilerReferenceIndexerScheduler])
}

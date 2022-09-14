package org.jetbrains.plugins.scala.compiler.references.indices

private[references] trait IndexerScheduler {
  def schedule(job: IndexingStage): Unit
  def scheduleAll(jobs: Seq[IndexingStage]): Unit
}

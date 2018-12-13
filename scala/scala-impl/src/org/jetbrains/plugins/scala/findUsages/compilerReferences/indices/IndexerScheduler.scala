package org.jetbrains.plugins.scala.findUsages.compilerReferences.indices

private[compilerReferences] trait IndexerScheduler {
  def schedule(job: IndexingStage): Unit
  def scheduleAll(jobs: Seq[IndexingStage]): Unit
}

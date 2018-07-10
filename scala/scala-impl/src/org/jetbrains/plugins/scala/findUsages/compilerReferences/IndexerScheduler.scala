package org.jetbrains.plugins.scala.findUsages.compilerReferences

private trait IndexerScheduler {
  def schedule(job: IndexerJob): Unit
}

package org.jetbrains.plugins.scala.caches.stats

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, ToggleAction}

class PrintScalaCachesReportAction extends AnAction("Print scala plugin caches report") {

  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setEnabledAndVisible(CacheStatsCollector.isEnabled)
  }

  override def actionPerformed(event: AnActionEvent): Unit = {
    CacheStatsCollector.printReport()
  }
}

class ToggleScalaCacheTracingAction extends ToggleAction("Scala plugin caches tracing") {

  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setEnabledAndVisible(CacheStatsCollector.isAvailable)
  }

  def isSelected(e: AnActionEvent): Boolean = CacheStatsCollector.isEnabled

  def setSelected(e: AnActionEvent, state: Boolean): Unit = CacheStatsCollector.setEnabled(state)
}
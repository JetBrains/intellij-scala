package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.components.ServiceManager

object Stats {
  private def enabled: Boolean = ServiceManager.getService(classOf[UsageTrigger]) != null

  def trigger(feature: String): Unit = {
    if (enabled) {
      UsageTrigger.trigger(feature)
    }
  }
}

package org.jetbrains.plugins.scala.statistics

object Stats {
  private def enabled: Boolean = false

  def trigger(feature: FeatureKey): Unit = {
  }

  def trigger(condition: Boolean, feature: FeatureKey): Unit = {
    if (condition) trigger(feature)
  }
}

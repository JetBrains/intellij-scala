package org.jetbrains.plugins.scala.statistics

// TODO: fix/remove/rewrite! This is not doing anything currently!
object Stats {
  private def enabled: Boolean = false

  def trigger(feature: FeatureKey): Unit = {
  }

  def trigger(condition: Boolean, feature: FeatureKey): Unit = {
    if (condition) trigger(feature)
  }
}

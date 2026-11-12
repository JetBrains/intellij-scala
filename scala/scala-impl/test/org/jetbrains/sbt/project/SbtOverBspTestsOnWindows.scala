package org.jetbrains.sbt.project

import com.intellij.util.system.OS

/**
 * Switch for the sbt-over-BSP tests that are disabled on Windows (SCL-25750).
 *
 * The connection file regeneration tests fail on Windows for a reason that is not understood yet: the notification
 * about an externally changed connection file is not shown.
 *
 * `BspConfigRegenerationIntegrationTest` checks the switch once, at its entry point, for all its tests.
 */
object SbtOverBspTestsOnWindows {

  // TODO: SCL-25750 re-enable the sbt-over-BSP tests on Windows
  def disabled: Boolean = OS.CURRENT == OS.Windows
}

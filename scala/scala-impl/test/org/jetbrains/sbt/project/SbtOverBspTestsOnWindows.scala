package org.jetbrains.sbt.project

import com.intellij.util.system.OS

/**
 * Switch for the sbt-over-BSP tests that are disabled on Windows (SCL-26441).
 *
 * The connection file regeneration tests fail on Windows for a reason that is not understood yet: depending on the
 * test, the hash of the connection file is not recorded, the notification about an externally changed connection file
 * is not shown, or the BSP server still fails to start after the connection file was regenerated.
 *
 * `BspConfigRegenerationIntegrationTest` checks the switch once, at its entry point, for all its tests.
 */
object SbtOverBspTestsOnWindows {

  // TODO: SCL-26441 re-enable the sbt-over-BSP tests on Windows
  def disabled: Boolean = OS.CURRENT == OS.Windows
}

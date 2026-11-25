package org.jetbrains.sbt.shell

import com.intellij.util.system.OS

/**
 * Switches for the sbt shell tests that are disabled on Windows (SCL-26340), one per cause.
 *
 * Each family checks its switch once, at its entry point.
 */
object SbtShellTestsOnWindows {

  /**
   * With real sbt, the new sbt shell fails intermittently: imports find an empty structure file, and the echoed
   * commands in the shell output are broken into fragments.
   *
   * Checked by:
   *  - `SbtProjectStructureImportingRunner`: the `[new shell]` variants are not created
   *  - `SbtShellBuildDelegationTestBase`: the new sbt shell tests
   */
  // TODO: SCL-26340 re-enable the new sbt shell tests with real sbt on Windows
  def newShellWithRealSbtTestsDisabled: Boolean = OS.CURRENT == OS.Windows
}

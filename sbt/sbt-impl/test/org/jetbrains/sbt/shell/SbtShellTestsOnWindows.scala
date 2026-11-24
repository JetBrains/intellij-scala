package org.jetbrains.sbt.shell

import com.intellij.util.system.OS

/**
 * Switches for the sbt shell tests that are disabled on Windows (SCL-26340), one per cause.
 *
 * Each family checks its switch once, at its entry point.
 */
object SbtShellTestsOnWindows {

  /**
   * In neither sbt shell does the mock sbt process see the end-of-file character (Ctrl+D) of the EOF action. A Windows
   * console doesn't treat Ctrl+D as end of file: in line mode, it keeps it in the line and waits for Enter.
   *
   * Checked by:
   *  - `SbtShellEOFActionTestBase`: all its tests
   */
  // TODO: SCL-26340 re-enable the EOF action tests with the mock sbt process on Windows
  def mockProcessEndOfFileTestsDisabled: Boolean = OS.CURRENT == OS.Windows

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

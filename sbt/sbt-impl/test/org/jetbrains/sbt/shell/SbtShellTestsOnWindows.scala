package org.jetbrains.sbt.shell

import com.intellij.util.system.OS

/**
 * Switch for the sbt shell test families that are disabled on Windows (SCL-26340).
 *
 * Their Windows failures are not isolated: different tests from the same families fail from run to run.
 * The observed causes include sbt shell output hard-wrapped at 80 columns, commands that never reach the (mock) sbt
 * process, a lost ScalaTest test-finished event, a leaked JVM attach thread and the EOF action not stopping the shell.
 *
 * Each family checks [[disabled]] once, at its entry point:
 *  - `SbtRunConfiguration_MockedProcess_ExecutionTestBase`: the sbt shell scenarios of the mocked run configuration suites
 *  - `SbtRunConfiguration_BuildBeforeLaunch_TestBase` and `SbtTask_BeforeLaunchStep_AsSbtRunConfiguration_TestBase`:
 *    all their tests, because the no-shell scenarios are flaky on Windows as well
 *  - `SbtShellEOFActionTestBase`: all its tests
 *  - `SbtProjectStructureImportingRunner`: the `[new shell]` variants are not created
 *  - `SbtShellBuildDelegationTestBase`: the new sbt shell tests
 *  - `ScalaTestWithSbtShellAndUiTestBase`: all its tests
 */
object SbtShellTestsOnWindows {

  // TODO: SCL-26340 re-enable the sbt shell test families on Windows
  def disabled: Boolean = OS.CURRENT == OS.Windows
}

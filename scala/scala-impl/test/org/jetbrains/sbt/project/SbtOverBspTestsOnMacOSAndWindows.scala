package org.jetbrains.sbt.project

import com.intellij.util.system.OS

/**
 * Switch for the sbt-over-BSP tests that are disabled on macOS and Windows (SCL-25750).
 *
 * On macOS, generating the BSP connection file (`bspConfig`) fails to resolve sbt, because Maven Central answers with
 * 429 Too Many Requests: unlike the sbt import tests, the BSP launch does not use the repositories and caches set up by
 * [[SbtCachesSetupUtil]]. On Windows, the connection file regeneration tests fail for a reason that is not understood yet.
 *
 * Each case checks the switch once, at its entry point:
 *  - `SbtOverBspExternalSystemImportingTestCase`: all sbt-over-BSP import tests, on macOS; the connection file is not
 *    generated during their setup there either
 *  - `BspOpenProjectProviderCloseDuringImportTest.testClosingProjectDuringBspSessionInitializationCancelsWithoutExecutionError`,
 *    on macOS
 *  - `BspConfigRegenerationIntegrationTest`: all its tests, on Windows
 *  - `SbtOverBspProjectStructureImportingTestBase.testJavaLanguageLevelAndTargetByteCodeLevel`: the sbt 1 variant, on Windows
 */
object SbtOverBspTestsOnMacOSAndWindows {

  // TODO: SCL-25750 re-enable the sbt-over-BSP tests on macOS and Windows
  def disabledOnMacOS: Boolean = OS.CURRENT == OS.macOS

  // TODO: SCL-25750 re-enable the sbt-over-BSP tests on macOS and Windows
  def disabledOnWindows: Boolean = OS.CURRENT == OS.Windows
}

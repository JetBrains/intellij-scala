package org.jetbrains.sbt.shell

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.junit.experimental.categories.Category

/**
 * Duplicates [[SbtShellStateIntegrationTest]] with the mock sbt process enabled.
 *
 * The primary purpose is to indirectly verify that the mocked sbt shell behaves the same way as the real sbt shell for
 * startup, command execution, restart, shutdown, and shell-based project import.
 */
@Category(Array(classOf[SlowTests2]))
class SbtShellStateIntegrationTest_WithMockSbt extends SbtShellStateIntegrationTest {

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    // The old/new shell flavor is already selected by the base (SbtRuntimeTest_WithSbtShell sets the `sbt.new.shell`
    // registry from `useNewShell`); here we only need to substitute the mock process.
    MockSbtProcessForTestsSetup.enableMockSbtProcess(getMyProject, getTestRootDisposable)
  }
}

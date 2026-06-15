package org.jetbrains.sbt.shell

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.junit.experimental.categories.Category

/**
 * Duplicates [[SbtShellStateIntegrationTest]] with the mock sbt process enabled.
 *
 * The mock is a lightweight sbt-process substitute for straightforward shell lifecycle, command queue, output, restart,
 * shutdown, and shell-based project import scenarios. It intentionally does not emulate terminal internals such as raw
 * input mode, OS-level echo settings, or single-character interactive prompts; those behaviors should be covered with
 * real sbt.
 */
@Category(Array(classOf[SlowTests2]))
class SbtShellStateIntegrationTest_WithMockSbt extends SbtShellStateIntegrationTest {

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    MockSbtProcessForTestsSetup.enableMockSbtProcess(getMyProject, getTestRootDisposable)
  }
}

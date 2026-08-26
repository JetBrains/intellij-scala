package org.jetbrains.sbt.shell

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[SlowTests2]))
class SbtShellWithTerminalPropsIntegrationTest extends SbtRuntimeTest_WithSbtShell {

  override protected def getRelativeTestProjectPath: String = "sbt-shell-runtime-tests/testdata/sbt/shell/testShellState"

  def testShellStartsWithSbtTerminalProps(): Unit = {
    SbtSettings.getInstance(getMyProject).setSbtEnvironment(
      Map("SBT_TERMINAL_PROPS" -> "0,0,false,false,false").asJava
    )

    SbtProcessManager.forProject(getMyProject).destroyProcess()
    SbtShellTestUtil.waitUntilSbtShellIsReady(
      getMyProject,
      DefaultCommandWaitTimeout,
      "sbt shell did not become ready when SBT_TERMINAL_PROPS was configured"
    )
    assertEquals(ShellState.Idle, comm.currentState)
  }
}

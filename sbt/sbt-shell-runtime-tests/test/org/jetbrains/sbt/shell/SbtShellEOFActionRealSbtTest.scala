package org.jetbrains.sbt.shell

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.shell.action.EOFAction
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.concurrent.duration.DurationInt

/**
 * Checks that the EOF action (Ctrl+D) stops real sbt in the sbt shell.
 *
 * [[SbtShellEOFActionTestBase]] checks the same with the mock sbt process. On Windows, the mock has to read the console
 * in a special way to see Ctrl+D at all (SCL-26340), so this test makes sure the EOF action also works with real sbt.
 */
@RunWith(classOf[JUnit4])
abstract class SbtShellEOFActionRealSbtTestBase extends SbtRuntimeTest_WithSbtShell {

  override protected def getRelativeTestProjectPath: String = "sbt-shell-runtime-tests/testdata/sbt/shell/testShellState"

  override protected def importProjectDuringTestSetUp: Boolean = false

  @Test
  def sbtShellEOFActionStopsRealSbt(): Unit = {
    val project = getMyProject
    sbtShellFixture.waitForShellReady(project)

    val dataContext = SimpleDataContext.getProjectContext(project)
    invokeAndWait {
      new EOFAction(project).actionPerformed(TestActionEvent.createTestEvent(dataContext))
    }

    def isStopped: Boolean = shellCommunication.currentState.isOff && !SbtProcessManager.forProject(project).isAlive
    AwaitTestUtils.waitConditioned(60.seconds)(() => isStopped)
    if (!isStopped) fail(s"Real sbt should stop after the EOF action (in a 60 seconds time frame)$shellDiagnostics")
  }
}

class SbtShellEOFActionRealSbtTest_OldShell extends SbtShellEOFActionRealSbtTestBase

class SbtShellEOFActionRealSbtTest_NewShell extends SbtShellEOFActionRealSbtTestBase {
  override protected def useNewShell: Boolean = true
}

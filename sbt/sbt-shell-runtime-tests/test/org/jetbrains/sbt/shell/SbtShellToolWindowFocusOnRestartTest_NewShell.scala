package org.jetbrains.sbt.shell

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.jetbrains.sbt.runner.consoleOutput.SbtShellToolWindowActivationTestUtil.installSbtShellToolWindowActivationProbe
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

/**
 * Restarting the new (terminal-based) sbt shell must focus its tool window,
 * like the legacy shell does via [[SbtShellRunner#showConsole]].
 */
@Category(Array(classOf[SlowTests2]))
class SbtShellToolWindowFocusOnRestartTest_NewShell extends SbtRuntimeTest_WithSbtShell {

  override protected def useNewShell: Boolean = true

  override protected def getRelativeTestProjectPath: String = "sbt-shell-runtime-tests/testdata/sbt/shell/testShellState"

  override protected def importProjectDuringTestSetUp: Boolean = false

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    MockSbtProcessForTestsSetup.enableMockSbtProcess(getMyProject, getTestRootDisposable)
  }

  override def setUp(): Unit = {
    super.setUp()
    sbtShellFixture.waitForShellReady(project)
  }

  private def project: Project = getMyProject

  def testRestartActivatesTheShellToolWindowWithFocus(): Unit = {
    // The probe restores the real `ToolWindowManager` when this disposable is disposed.
    // `getTestRootDisposable` would be too late: it is disposed after the project.
    val probeDisposable = Disposer.newDisposable("sbt shell tool window activation probe")
    try {
      val activationProbe = installSbtShellToolWindowActivationProbe(
        project,
        createContentOnFirstGet = false,
        probeDisposable,
      )
      val baseline = activationProbe.snapshot()

      SbtProcessManager.forProject(project).restartProcess()
      sbtShellFixture.waitForShellReady(project)

      val afterRestart = activationProbe.snapshot()

      assertTrue(
        s"""restarting the sbt shell must activate its tool window, but nothing was activated
           |activations before restart: ${baseline.activationCount}, after restart: ${afterRestart.activationCount}""".stripMargin + shellDiagnostics,
        afterRestart.activationCount > baseline.activationCount
      )
      assertTrue(
        s"""restarting the sbt shell must activate its tool window with focus, but the activation did not request focus
           |focusing activations before restart: ${baseline.autoFocusActivationCount}, after restart: ${afterRestart.autoFocusActivationCount}""".stripMargin + shellDiagnostics,
        afterRestart.autoFocusActivationCount > baseline.autoFocusActivationCount
      )
    } finally {
      Disposer.dispose(probeDisposable)
    }
  }
}

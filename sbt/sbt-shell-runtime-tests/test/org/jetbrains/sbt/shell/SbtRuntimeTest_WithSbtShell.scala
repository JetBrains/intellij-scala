package org.jetbrains.sbt.shell

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.SbtRuntimeTestBase
import org.junit.experimental.categories.Category

import scala.compiletime.uninitialized
import scala.concurrent.duration.FiniteDuration

@Category(Array(classOf[SlowTests2]))
abstract class SbtRuntimeTest_WithSbtShell extends SbtRuntimeTestBase {

  protected def useNewShell: Boolean = false

  override protected def getTestSbtProjectSettings =
    super.getTestSbtProjectSettings.copy(useSbtShellForImport = true)

  protected def comm: SbtShellCommunication = sbtShellFixture.getSbtShellCommunication

  protected def shellProcessHandler: OSProcessHandler = sbtShellFixture.getSbtShellProcessHandler

  protected def processListener: SbtShellTestUtil.TestSbtShellProcessListener = sbtShellFixture.getTestSbtShellProcessListener

  private var sbtShellFixture: SbtShellTestFixture = uninitialized

  protected val DefaultCommandWaitTimeout: FiniteDuration = SbtShellTestFixture.DefaultCommandWaitTimeout

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    SbtShellTestUtil.setNewSbtShellEnabled(useNewShell, getTestRootDisposable)
  }

  override def setUp(): Unit = {
    super.setUp()

    sbtShellFixture = new SbtShellTestFixture(getMyProject)
    Disposer.register(getTestRootDisposable, sbtShellFixture)
    sbtShellFixture.setUp()
  }
}

package org.jetbrains.sbt.shell

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.SbtRuntimeTestBase
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector
import org.jetbrains.sbt.shell.communication.SbtShellCommandRequestId
import org.junit.Assert.{assertFalse, assertTrue}
import org.junit.experimental.categories.Category

import scala.compiletime.uninitialized
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

@Category(Array(classOf[SlowTests2]))
abstract class SbtRuntimeTest_WithSbtShell extends SbtRuntimeTestBase {

  protected def useNewShell: Boolean = false

  override protected def getTestSbtProjectSettings =
    super.getTestSbtProjectSettings.copy(useSbtShellForImport = true)

  //TODO: leave just one method
  protected def comm: SbtShellCommunication = sbtShellFixture.getSbtShellCommunication
  protected def shellCommunication: SbtShellCommunication = comm

  protected def shellProcessHandler: OSProcessHandler = sbtShellFixture.getSbtShellProcessHandler

  protected def processListener: SbtShellTestUtil.TestSbtShellProcessListener = sbtShellFixture.getTestSbtShellProcessListener

  protected var sbtShellFixture: SbtShellTestFixture = uninitialized

  // SbtShellTestFixture setUp requires BGT
  override def runInDispatchThread(): Boolean = false

  protected val DefaultCommandWaitTimeout: FiniteDuration = SbtShellTestFixture.DefaultCommandWaitTimeout

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    SbtShellTestUtil.setNewSbtShellEnabled(useNewShell, getTestRootDisposable)
  }

  override def setUp(): Unit = {
    super.setUp()

    val project = getMyProject

    testProjectJdk.setUp()
    testProjectJdk.setAsProjectJdk(project)

    sbtShellFixture = new SbtShellTestFixture(project)
    Disposer.register(getTestRootDisposable, sbtShellFixture)
    sbtShellFixture.setUp()
  }

  /** Renders a titled diagnostics section, substituting a placeholder for empty bodies. */
  protected final def diagnosticSection(title: String, body: String): String = {
    val text = Option(body).filter(_.nonEmpty).getOrElse("<empty>")
    s"""===== $title =====
       |$text""".stripMargin
  }

  /**
   * Generic sbt-shell diagnostics captured live at call time: the sbt shell log, the shared raw sbt
   * process output, and the [[SbtShellCommunication]] snapshot.
   */
  protected def shellDiagnostics: String =
    Seq(
      diagnosticSection("Captured sbt shell log", processListener.getLog),
      diagnosticSection("Shared raw sbt process output", SbtProcessOutputDiagnosticsCollector.sharedProcessOutput),
      diagnosticSection("Sbt shell communication snapshot", shellCommunication.diagnosticsSnapshot),
    ).mkString("\n", "\n", "")

  /** Asserts that at least one diagnostic event of type `T` with the given `expectedRequestId` exists. */
  def assertDiagnosticEventExists[T <: SbtShellDiagnosticEvent.CommandEvent](
    events: Seq[SbtShellDiagnosticEvent],
    expectedRequestId: SbtShellCommandRequestId,
    snapshot: String
  )(using ct: ClassTag[T]): Unit =
    assertTrue(
      s"Missing ${ct.runtimeClass.getSimpleName} event with requestId=${expectedRequestId.value}. Snapshot: $snapshot",
      hasDiagnosticEvent[T](events, expectedRequestId)
    )

  /** Asserts that no diagnostic event of type `T` with the given `expectedRequestId` exists. */
  protected def assertDiagnosticEventNotExists[T <: SbtShellDiagnosticEvent.CommandEvent](
    events: Seq[SbtShellDiagnosticEvent],
    expectedRequestId: SbtShellCommandRequestId,
    snapshot: String
  )(using ct: ClassTag[T]): Unit =
    assertFalse(
      s"Unexpected ${ct.runtimeClass.getSimpleName} event with requestId=${expectedRequestId.value} was found. Snapshot: $snapshot",
      hasDiagnosticEvent[T](events, expectedRequestId)
    )

  private def hasDiagnosticEvent[T <: SbtShellDiagnosticEvent.CommandEvent : ClassTag](
    events: Seq[SbtShellDiagnosticEvent],
    expectedRequestId: SbtShellCommandRequestId,
  ): Boolean =
    events.exists { case x: T => x.requestId == expectedRequestId; case _ => false }

  /** Asserts that at least one diagnostic event of type `T` exists. */
  def assertDiagnosticEventExists[T <: SbtShellDiagnosticEvent](
    events: Seq[SbtShellDiagnosticEvent],
    snapshot: String,
  )(using ct: ClassTag[T]): Unit =
    assertTrue(
      s"Missing ${ct.runtimeClass.getSimpleName} event. Snapshot: $snapshot",
      events.exists(ct.runtimeClass.isInstance)
    )

  /** Asserts that no diagnostic event of type `T` with the given `expectedRequestId` exists. */
  protected def assertDiagnosticEventNotExists[T <: SbtShellDiagnosticEvent](
    events: Seq[SbtShellDiagnosticEvent],
    snapshot: String,
  )(using ct: ClassTag[T]): Unit =
    assertFalse(
      s"Unexpected ${ct.runtimeClass.getSimpleName} event was found. Snapshot: $snapshot",
      events.exists(ct.runtimeClass.isInstance)
    )
}

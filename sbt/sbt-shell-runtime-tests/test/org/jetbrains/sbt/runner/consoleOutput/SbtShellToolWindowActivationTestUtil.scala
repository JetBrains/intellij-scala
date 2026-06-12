package org.jetbrains.sbt.runner.consoleOutput

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.{ToolWindow, ToolWindowManager}
import com.intellij.testFramework.{PlatformTestUtil, ServiceContainerUtil}
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.sbt.runner.TestExecutionOptions
import org.jetbrains.sbt.shell.SbtShellToolWindowFactory
import org.junit.Assert.assertEquals

import java.util.concurrent.atomic.AtomicInteger

/**
 * Test helpers for checking that run configurations starting sbt shell do not activate its tool window.
 */
private[runner] object SbtShellToolWindowActivationTestUtil {

  final class ActivationProbe private[consoleOutput] (state: () => ToolWindowOpenState) {
    def snapshot(): ToolWindowOpenState = {
      drainToolWindowActivationEvents()
      state()
    }

    def assertUnchangedSince(baseline: ToolWindowOpenState, sbtShellModeDisplayName: String): Unit = {
      val actual = snapshot()
      assertEquals(
        s"Run configuration must not show, activate, or focus the sbt shell tool window for $sbtShellModeDisplayName process",
        baseline,
        actual,
      )
    }
  }

  final case class ToolWindowOpenState(
    showCount: Int,
    activationCount: Int,
    autoFocusActivationCount: Int,
  )

  def installSbtShellToolWindowActivationProbeIfNeeded(
    options: TestExecutionOptions,
    project: Project,
    parentDisposable: Disposable,
  ): Option[ActivationProbe] =
    if (options.useSbtShellInRunConfig) {
      val manager = new RecordingToolWindowManager(
        project,
        createContentOnFirstGet = !options.prestartSbtShell,
      )
      ServiceContainerUtil.replaceService(
        project,
        classOf[ToolWindowManager],
        manager,
        parentDisposable,
      )
      manager.doRegisterToolWindow(SbtShellToolWindowFactory.ID)
      Some(new ActivationProbe(() => manager.openState(SbtShellToolWindowFactory.ID)))
    } else {
      None
    }

  def captureSbtShellToolWindowActivationBaselineIfNeeded(
    activationProbe: Option[ActivationProbe],
  ): Option[ToolWindowOpenState] =
    activationProbe.map(_.snapshot())

  def assertSbtShellToolWindowWasNotOpenedByRunConfigurationIfNeeded(
    activationProbe: Option[ActivationProbe],
    baseline: Option[ToolWindowOpenState],
    sbtShellModeDisplayName: String,
  ): Unit =
    for {
      probe <- activationProbe
      state <- baseline
    } probe.assertUnchangedSince(state, sbtShellModeDisplayName)

  private def drainToolWindowActivationEvents(): Unit =
    invokeAndWait {
      UIUtil.dispatchAllInvocationEvents()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

  private final class RecordingToolWindowManager(
    project: Project,
    createContentOnFirstGet: Boolean,
  ) extends ToolWindowHeadlessManagerImpl(project) {
    private val toolWindows = scala.collection.mutable.Map.empty[String, RecordingToolWindow]
    private val contentCreated = scala.collection.mutable.Set.empty[String]

    override def doRegisterToolWindow(id: String): ToolWindow = {
      val toolWindow = new RecordingToolWindow(project)
      toolWindows(id) = toolWindow
      toolWindow
    }

    override def getToolWindow(id: String): ToolWindow = {
      val toolWindow = toolWindows.getOrElse(id, null)
      if (createContentOnFirstGet && id == SbtShellToolWindowFactory.ID && toolWindow != null && !contentCreated.contains(id)) {
        contentCreated += id
        new SbtShellToolWindowFactory().createToolWindowContent(project, toolWindow)
      }
      toolWindow
    }

    def openState(id: String): ToolWindowOpenState =
      toolWindows.get(id).fold(ToolWindowOpenState(0, 0, 0))(_.openState)
  }

  private final class RecordingToolWindow(project: Project) extends ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
    private val shows = new AtomicInteger()
    private val activations = new AtomicInteger()
    private val autoFocusActivations = new AtomicInteger()

    override def show(runnable: Runnable): Unit = {
      shows.incrementAndGet()
      if (runnable != null) {
        runnable.run()
      }
    }

    override def activate(runnable: Runnable, autoFocusContents: Boolean, forced: Boolean): Unit = {
      activations.incrementAndGet()
      if (autoFocusContents) {
        autoFocusActivations.incrementAndGet()
      }
      super.activate(runnable, autoFocusContents, forced)
    }

    def openState: ToolWindowOpenState =
      ToolWindowOpenState(shows.get(), activations.get(), autoFocusActivations.get())
  }
}

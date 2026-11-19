package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.ide.lightEdit.{LightEdit, LightEditService, LightEditServiceImpl}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.testFramework.{DumbModeTestUtils, LightPlatformTestCase, PlatformTestUtil, TestLoggerKt}
import org.jetbrains.plugins.scala.compiler.highlighting.services.{BackgroundExecutorService, CompilationLogicService}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}

abstract class ToggleHighlightingModeListenerTestBase extends LightPlatformTestCase {
  /** Delivers the roots callback explicitly because production listeners are disabled in unit-test mode. */
  protected final def notifyModuleRootsChanged(project: Project): Unit = {
    val event = new ModuleRootEvent(project) {
      override def isCausedByFileTypesChange: Boolean = false
      override def isCausedByWorkspaceModelChangesOnly: Boolean = false
    }
    new ToggleHighlightingModeListener.OnModuleRootChange(project).rootsChanged(event)
  }

  /** Finishes queued listener work without creating a compiler executor in LightEdit. */
  protected final def waitForBackgroundWork(project: Project): Unit = {
    // First deliver work deferred until smart mode, then finish the background work and its UI callbacks.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Option(project.getServiceIfCreated(classOf[BackgroundExecutorService])).foreach { service =>
      PlatformTestUtil.waitForFuture(service.executor.submit[Unit](() => ()), 30000)
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }
}

/** SCL-25841: roots callbacks must be safe too; editor integration tests cover settings events. */
final class ToggleHighlightingModeListenerLightEditTest extends ToggleHighlightingModeListenerTestBase {
  private def lightEditService: LightEditServiceImpl =
    LightEditService.getInstance().asInstanceOf[LightEditServiceImpl]

  def testModuleRootChangeIsSafeInLightEdit(): Unit = checkModuleRootChangeWithoutIdeErrors()

  /** Exercises the listener entry point that is not reached by the editor integration tests. */
  private def checkModuleRootChangeWithoutIdeErrors(): Unit = {
    val project = lightEditService.getOrCreateProject()
    assertTrue(LightEdit.owns(project))
    TestLoggerKt.assertNothingLogged(false, () => {
      notifyModuleRootsChanged(project)
      waitForBackgroundWork(project)
      kotlin.Unit.INSTANCE
    })
  }

  override protected def tearDown(): Unit = {
    try lightEditService.disposeCurrentSession()
    finally super.tearDown()
  }
}

final class ToggleHighlightingModeListenerProjectTest extends ToggleHighlightingModeListenerTestBase {
  def testHighlightingChangeInvalidatesCompilationState(): Unit = {
    val logic = CompilationLogicService(getProject)
    val epoch = logic.snapshot.changesEpoch
    assertFalse(LightEdit.owns(getProject))
    notifyHighlightingChanged()
    waitForBackgroundWork(getProject)
    assertEquals(epoch + 1, logic.snapshot.changesEpoch)
  }

  def testModuleRootChangeInvalidatesCompilationState(): Unit = {
    val logic = CompilationLogicService(getProject)
    val epoch = logic.snapshot.changesEpoch
    notifyModuleRootsChanged(getProject)
    waitForBackgroundWork(getProject)
    assertEquals(epoch + 1, logic.snapshot.changesEpoch)
  }

  def testHighlightingChangeIsDeferredUntilSmartMode(): Unit = checkHighlightingChangeDuringIndexing()

  /** Verifies both that indexing postpones invalidation and that returning to smart mode completes it. */
  private def checkHighlightingChangeDuringIndexing(): Unit = {
    val project = getProject
    val logic = CompilationLogicService(project)
    val epoch = logic.snapshot.changesEpoch
    DumbModeTestUtils.runInDumbModeSynchronously(project, () => {
      assertTrue(DumbService.isDumb(project))
      notifyHighlightingChanged()
      waitForBackgroundWork(project)
      assertEquals("Indexing must defer the work", epoch, logic.snapshot.changesEpoch)
    })

    assertFalse(DumbService.isDumb(project))
    waitForBackgroundWork(project)
    assertEquals("Deferred work must run after indexing", epoch + 1, logic.snapshot.changesEpoch)
  }

  /** Delivers one representative settings callback; the callback argument does not change the setting. */
  private def notifyHighlightingChanged(): Unit =
    new ToggleHighlightingModeListener.OnCompilerHighlightingChange(getProject).compilerHighlightingScala3Changed(true)
}

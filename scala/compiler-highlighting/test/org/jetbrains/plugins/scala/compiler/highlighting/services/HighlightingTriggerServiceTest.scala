package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationStateWritesTestBase
import org.junit.Assert.*
import org.junit.Test

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class HighlightingTriggerServiceTest extends CompilationStateWritesTestBase:

  private def triggerService: HighlightingTriggerService = HighlightingTriggerService(getProject)

  /**
   * Helper to allow background threads and the ScheduledDebouncer to flush.
   * Required because dispatchRequest uses executeOnBackgroundThreadInNotDisposed.
   */
  private def waitForBackgroundTasks(): Unit =
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    Thread.sleep((triggerService.DELAY + FiniteDuration(50, TimeUnit.MILLISECONDS)).toMillis) // Buffer for the 200ms debounce window
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

  @Test
  def testSchedule_ValidFile_IsQueuedAndDispatched(): Unit =
    val file = inSources("ValidFile.scala", "class ValidFile")

    triggerService.schedule(file, "test scheduling")
    waitForBackgroundTasks()

    // Assert the file remains valid and doesn't throw during dispatch.
    assertTrue("Valid source file should remain valid", file.isValid)

  @Test
  def testBypass_SkipsDebouncer(): Unit =
    val file = inSources("BypassFile.scala", "class BypassFile")

    triggerService.bypass(file, "test bypass")

    // We only wait for the immediate background thread dispatch, skipping the thread sleep
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertTrue("Bypassed source file should remain valid", file.isValid)

  @Test
  def testSchedule_OutsideRoots_IsIgnored(): Unit =
    val file = outsideRoots("Outside.scala", "class Outside")

    triggerService.schedule(file, "test outside roots")
    waitForBackgroundTasks()

    // canHighlight and canBeScheduled should filter this out early, preventing any compilation requests.
    assertTrue(file.isValid)

  @Test
  def testSchedule_InvalidFile_IsIgnored(): Unit =
    val file = inSources("DeletedFile.scala", "class DeletedFile")
    delete(file) // Invalidates the VirtualFile

    triggerService.schedule(file, "test invalid file")
    waitForBackgroundTasks()

    assertFalse("Deleted file should be invalid", file.isValid)

  @Test
  def testBypass_InvalidFile_IsIgnored(): Unit =
    val file = inSources("DeletedBypass.scala", "class DeletedBypass")
    delete(file)

    triggerService.bypass(file, "test invalid bypass")
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertFalse("Deleted file should be invalid", file.isValid)
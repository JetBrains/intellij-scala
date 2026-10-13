package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.EdtTestUtil.runInEdtAndWait
import com.intellij.testFramework.{DumbModeTestUtils, PlatformTestUtil, ServiceContainerUtil}
import org.jetbrains.plugins.scala.compiler.highlighting.services.HighlightingService
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateWritesTestBase
import org.jetbrains.plugins.scala.compiler.highlighting.util.SpyHighlightingService
import org.junit.Assert.{assertEquals, assertFalse, assertSame, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.compiletime.uninitialized

@RunWith(classOf[JUnit4])
final class DumbModeListenerTest extends CompilationStateWritesTestBase:

  private var spyCompilerService: SpyHighlightingService = uninitialized

  override def setUp(): Unit =
    super.setUp()

    spyCompilerService = new SpyHighlightingService(getProject)

    ServiceContainerUtil.replaceService(
      getProject,
      classOf[HighlightingService],
      spyCompilerService,
      getTestRootDisposable
    )

    assertSame(
      spyCompilerService,
      HighlightingService(getProject)
    )

  @Test
  def testExitDumbModeRequestsHighlighting(): Unit =
    // Remove unrelated events left over from project setup.
    runInEdtAndWait { () =>
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
    spyCompilerService.parameterlessRequestCount = 0

    DumbModeTestUtils.runInDumbModeSynchronously(
      getProject,
      () => assertTrue(DumbService.isDumb(getProject))
    )

    assertFalse(DumbService.isDumb(getProject))

    // DumbModeListener notifications are posted asynchronously to the EDT.
    runInEdtAndWait { () =>
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    assertEquals(1, spyCompilerService.parameterlessRequestCount)


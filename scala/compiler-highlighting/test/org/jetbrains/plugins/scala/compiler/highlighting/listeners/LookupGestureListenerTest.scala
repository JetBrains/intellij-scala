package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.codeInsight.lookup.*
import com.intellij.testFramework.EdtTestUtil.runInEdtAndWait
import com.intellij.testFramework.ServiceContainerUtil
import org.jetbrains.plugins.scala.compiler.highlighting.services.TriggerService
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateWritesTestBase
import org.jetbrains.plugins.scala.compiler.highlighting.util.SpyTriggerService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.compiletime.uninitialized

@RunWith(classOf[JUnit4])
final class LookupGestureListenerTest extends CompilationStateWritesTestBase:

  private var spyTriggerService: SpyTriggerService = uninitialized

  override def setUp(): Unit =
    super.setUp()

    spyTriggerService = new SpyTriggerService(getProject)

    ServiceContainerUtil.replaceService(
      getProject,
      classOf[TriggerService],
      spyTriggerService,
      getTestRootDisposable
    )

    assertSame(
      "Service wasn't replaced",
      spyTriggerService,
      TriggerService(getProject)
    )

  @Test
  def testSchedulesWhenActiveLookupDisappears(): Unit =
    runInEdtAndWait { () =>
      val file = inSources("Code.scala", "class Code")
      myFixture.openFileInEditor(file)

      val editor = myFixture.getEditor
      val lookupManager = LookupManager.getInstance(getProject)

      val items = Array[LookupElement](
        LookupElementBuilder.create("first"),
        LookupElementBuilder.create("second")
      )

      val lookup = lookupManager.showLookup(
        editor,
        items,
        "",
        new LookupArranger.DefaultArranger()
      )

      assertNotNull("Lookup wasn't shown", lookup)
      assertSame(lookup, LookupManager.getActiveLookup(editor))
      assertTrue(spyTriggerService.scheduled.isEmpty)

      try
        lookupManager.hideActiveLookup()

        assertNull(LookupManager.getActiveLookup(editor))
        assertEquals(
          List(file -> "Lookup changed"),
          spyTriggerService.scheduled
        )
      finally
        lookupManager.hideActiveLookup()
    }

package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.{TemplateManagerImpl, TemplateState}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
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
final class TemplateListenerTest extends CompilationStateWritesTestBase:

  private var spyTriggerService: SpyTriggerService = uninitialized

  override def setUp(): Unit =
    super.setUp()

    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable)

    spyTriggerService = new SpyTriggerService(getProject)
    ServiceContainerUtil.replaceService(
      getProject,
      classOf[TriggerService],
      spyTriggerService,
      getTestRootDisposable
    )

    assertSame(
      spyTriggerService,
      TriggerService(getProject)
    )

  @Test
  def testSchedulesHighlightingWhenTemplateFinishes(): Unit =
    runInEdtAndWait { () =>
      val (state, file) = startTemplate()

      WriteCommandAction.runWriteCommandAction(
        getProject,
        new Runnable:
          override def run(): Unit = state.gotoEnd(false)
      )

      assertTrue(
        spyTriggerService.scheduled.contains(file -> "Template finished")
      )
    }

  @Test
  def testSchedulesHighlightingWhenTemplateIsCancelled(): Unit =
    runInEdtAndWait { () =>
      val (state, file) = startTemplate()

      state.cancelTemplate()

      assertTrue(
        spyTriggerService.scheduled.contains(file -> "Template canceled")
      )
    }

  private def startTemplate(): (TemplateState, VirtualFile) =
    val file = inSources("Code.scala", "class Code")
    myFixture.openFileInEditor(file)

    val manager = TemplateManager.getInstance(getProject)
    val template = manager.createTemplate(
      "test",
      "test",
      "$VALUE$"
    )
    template.addVariable("VALUE", "", "", true)

    val state = manager.runTemplate(myFixture.getEditor, template)

    assertFalse(state.isFinished)
    assertSame(
      state,
      TemplateManagerImpl.getTemplateState(myFixture.getEditor)
    )

    state -> file


package org.jetbrains.plugins.scala.lang.formatter.scalafmt

import com.intellij.openapi.actionSystem.{ActionManager, ActionUiKind, ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EdtTestUtil
import junit.framework.TestCase.{assertEquals, assertFalse, assertNotNull, assertSame, assertTrue}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.Path

/**
 * Exercises Scalafmt-on-save through the registered Save All action, starting with an
 * unsaved Scala document. This covers action dispatch and the plugin listener together.
 * The platform's production Save All replacement is disabled in unit test mode, so the
 * replacement test installs a representative proxy around the registered action.
 */
class ScalafmtReformatOnSaveActionIntegrationTest
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaFmtForTestsSetupOps {

  private val SaveAllActionId = "SaveAll"
  private val InitialScalaFileText = "object SaveOnFileSave {}\n"
  private val UnformattedScalaFileText = "object SaveOnFileSave{val x=1}\n"
  private val FormattedScalaFileText = "object SaveOnFileSave { val x = 1 }\n"

  override protected def scalafmtConfigsBasePath: Path =
    Path.of(TestUtils.getTestDataPath, "formatter", "scalafmt")

  override def runInDispatchThread(): Boolean = false

  override def setUp(): Unit = {
    super.setUp()
    ScalaFmtForTestsSetupOps.ensureDownloaded(DefaultVersion)
    getScalaCodeStyleSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE = true
    Registry.get("scalafmt.min.time.between.format.on.save.action.ms").setValue(0, getTestRootDisposable)
  }

  /** Verifies that the registered Save All action formats and saves an edited Scala file. */
  def testRegisteredSaveAllActionReformatsEditedScalaFile(): Unit = {
    openScalaFileWithUnsavedUnformattedChanges()
    performRegisteredSaveAllAndCheckFormatting()
  }

  /**
   * Verifies that replacing the registered Save All action with a delegating proxy
   * still triggers and saves Scalafmt output. This reproduces the action-identity
   * regression while exercising the same action dispatch as the plain Save All case.
   */
  def testRegisteredSaveAllReplacementReformatsEditedScalaFile(): Unit = {
    openScalaFileWithUnsavedUnformattedChanges()
    performProxiedRegisteredSaveAllAndCheckFormatting()
  }

  // TODO(IJPL-261322): Once JUnit installs the production save-action customizers,
  // test the real registered SaveAll/SaveDocument wrappers and reassess this proxy case.
  private def performProxiedRegisteredSaveAllAndCheckFormatting(): Unit = {
    val actionManager = ActionManager.getInstance()
    val original = registeredSaveAllAction()
    val replacement = new DumbAwareAction(original.getTemplatePresentation.getText) {
      override def update(event: AnActionEvent): Unit = original.update(event)
      override def actionPerformed(event: AnActionEvent): Unit = original.actionPerformed(event)
      override def getActionUpdateThread: ActionUpdateThread = original.getActionUpdateThread
    }

    EdtTestUtil.runInEdtAndWait(() => actionManager.replaceAction(SaveAllActionId, replacement))
    try {
      assertSame(replacement, registeredSaveAllAction())
      performRegisteredSaveAllAndCheckFormatting()
    } finally {
      EdtTestUtil.runInEdtAndWait(() => actionManager.replaceAction(SaveAllActionId, original))
    }
  }

  private def openScalaFileWithUnsavedUnformattedChanges(): Unit = {
    configureFromFileText(InitialScalaFileText)
    val document = myFixture.getEditor.getDocument

    EdtTestUtil.runInEdtAndWait(() => {
      val edit: Runnable = () => document.setText(UnformattedScalaFileText)
      WriteCommandAction.runWriteCommandAction(getProject, edit)
    })
    assertTrue(
      "The edited Scala document must be unsaved before Save All",
      FileDocumentManager.getInstance().isDocumentUnsaved(document)
    )
    assertEquals(
      "The unsaved edit must not have reached the file before Save All",
      InitialScalaFileText,
      VfsUtilCore.loadText(myFixture.getFile.getVirtualFile)
    )
  }

  /** Performs the registered Save All action and checks its formatted result is saved. */
  private def performRegisteredSaveAllAndCheckFormatting(): Unit = {
    val action = registeredSaveAllAction()
    val event = createSaveAllActionEvent(action)
    performSaveAllAction(action, event)
    assertEditedScalaFileWasFormattedAndSaved()
  }

  private def registeredSaveAllAction(): AnAction = {
    val action = ActionManager.getInstance().getAction(SaveAllActionId)
    assertNotNull("SaveAll must be registered", action)
    action
  }

  private def createSaveAllActionEvent(action: AnAction): AnActionEvent = {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject)
      .add(CommonDataKeys.EDITOR, myFixture.getEditor)
      .build()
    AnActionEvent.createEvent(action, dataContext, null, "", ActionUiKind.NONE, null)
  }

  private def performSaveAllAction(action: AnAction, event: AnActionEvent): Unit = {
    EdtTestUtil.runInEdtAndWait(() => {
      ActionUtil.updateAction(action, event)
      assertTrue("SaveAll must be enabled", event.getPresentation.isEnabled)
      val result = ActionUtil.performAction(action, event)
      assertTrue(s"SaveAll was not performed: $result", result.isPerformed)
      PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    }, false)
  }

  private def assertEditedScalaFileWasFormattedAndSaved(): Unit = {
    myFixture.checkResult(FormattedScalaFileText)
    assertFalse(
      "The formatted Scala document must be saved by Save All",
      FileDocumentManager.getInstance().isDocumentUnsaved(myFixture.getEditor.getDocument)
    )
    assertEquals(
      "Save All must persist the formatted Scala text in the file",
      FormattedScalaFileText,
      VfsUtilCore.loadText(myFixture.getFile.getVirtualFile)
    )
  }
}

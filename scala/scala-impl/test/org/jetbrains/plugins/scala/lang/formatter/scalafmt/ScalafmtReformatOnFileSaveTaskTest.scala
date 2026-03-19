package org.jetbrains.plugins.scala.lang.formatter.scalafmt

import com.intellij.ide.actions.SaveAllAction
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.{EdtTestUtil, TestActionEvent}
import junit.framework.TestCase.assertFalse
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtReformatOnFileSaveTask
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.Path

class ScalafmtReformatOnFileSaveTaskTest
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaFmtForTestsSetupOps {

  override protected def scalafmtConfigsBasePath: Path =
    Path.of(TestUtils.getTestDataPath, "formatter", "scalafmt")

  override def runInDispatchThread(): Boolean = false

  override def setUp(): Unit = {
    super.setUp()

    ScalaFmtForTestsSetupOps.ensureDownloaded(DefaultVersion)
    getScalaCodeStyleSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE = true

    // Registered in plugin.xml as an application-level listener, but this test publishes
    // AnActionListener events via project.messageBus. Without this explicit subscription,
    // no listener would receive the test event and formatting would not be triggered.
    getProject.getMessageBus
      .connect(getTestRootDisposable)
      .subscribe(AnActionListener.TOPIC, new ScalafmtReformatOnFileSaveTask)
  }

  def testSaveAllActionReformatsScalaFileInProjectSources(): Unit = {
    configureFromFileText("object SaveOnFileSave{val x=1}\n")

    invokeSaveAllAction()

    myFixture.checkResult("object SaveOnFileSave { val x = 1 }\n")
  }

  private def invokeSaveAllAction(): Unit = {
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(getProject))

    EdtTestUtil.runInEdtAndWait(() => {
      assertNoReadWriteLocksHeld()

      getProject.getMessageBus
        .syncPublisher(AnActionListener.TOPIC)
        .afterActionPerformed(new SaveAllAction, event, AnActionResult.PERFORMED)

      PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    }, false)
  }

  private def assertNoReadWriteLocksHeld(): Unit = {
    assertFalse(
      "The event should be dispatched without a read lock held to match the production behavior",
      ApplicationManager.getApplication.isReadAccessAllowed
    )
    assertFalse(
      "The event should be dispatched without a write lock held to match the production behavior",
      ApplicationManager.getApplication.isWriteAccessAllowed
    )
  }
}

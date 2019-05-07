package org.jetbrains.plugins.scala
package codeInsight
package intentions

import org.jetbrains.plugins.scala.editor._
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert.{assertFalse, assertTrue}

import scala.collection.JavaConverters._

/**
  * @author Ksenia.Sautina
  * @since 4/11/12
  */
abstract class ScalaIntentionTestBase  extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter._

  def familyName: String

  def caretTag: String = EditorTestUtil.CARET_TAG

  protected def doTest(text: String, resultText: String, expectedIntentionText: Option[String] = None): Unit = {
    import org.junit.Assert._
    implicit val project: Project = getProject

    findIntention(text) match {
      case Some(action) =>

        expectedIntentionText.foreach { expectedText =>
          assertEquals(expectedText, action.getText)
        }

        executeWriteActionCommand("Test Intention") {
          action.invoke(project, getEditor, getFile)
        }
      case None => fail("Intention is not found")
    }

    executeWriteActionCommand("Test Intention Formatting") {
      val document = FileDocumentManager.getInstance().getDocument(getFile.getVirtualFile)
      document.commit(project)
      CodeStyleManager.getInstance(project).reformat(getFile)
      val normalizedResultText = normalize(resultText)
      getFixture.checkResult(normalizedResultText)
    }
  }

  protected def checkIntentionIsNotAvailable(text: String): Unit =
    assertFalse("Intention is found", intentionIsAvailable(text))

  protected def checkIntentionIsAvailable(text: String): Unit =
    assertTrue("Intention is not found", intentionIsAvailable(text))

  private def findIntention(text: String): Option[IntentionAction] = {
    getFixture.configureByText(ScalaFileType.INSTANCE, normalize(text))
    getFixture.getAvailableIntentions.asScala
      .find(_.getFamilyName == familyName)
  }

  private def intentionIsAvailable(text: String): Boolean =
    findIntention(text).isDefined
}

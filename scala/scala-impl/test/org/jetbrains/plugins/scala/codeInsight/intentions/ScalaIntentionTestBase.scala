package org.jetbrains.plugins.scala
package codeInsight
package intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert.{assertFalse, assertTrue, fail}

import scala.collection.JavaConverters._

/**
  * @author Ksenia.Sautina
  * @since 4/11/12
  */
abstract class ScalaIntentionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter._

  def familyName: String

  protected def doTest(text: String, resultText: String): Unit = {
    val project = getProject

    findIntention(text) match {
      case Some(action) =>
        startCommand(project, "Test Intention") {
          action.invoke(project, getEditor, getFile)
        }
      case None => fail("Intention is not found")
    }

    startCommand(project, "Test Intention Formatting") {
      CodeStyleManager.getInstance(project).reformat(getFile)
      getFixture.checkResult(normalize(resultText))
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

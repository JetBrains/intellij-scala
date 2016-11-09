package org.jetbrains.plugins.scala
package codeInsight.intentions

import java.util

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.junit.Assert

import scala.collection.JavaConversions._

/**
 * @author Ksenia.Sautina
 * @since 4/11/12
 */

abstract class ScalaIntentionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  def familyName: String

  def doTest(text: String, resultText: String, familyName: String = this.familyName) {
    intentionByFamilyName(text, familyName) match {
      case Some(action) =>
        startCommand(getProject, "Test Intention") {
          action.invoke(myFixture.getProject, myFixture.getEditor, myFixture.getFile)
        }
      case None => Assert.fail("Intention is not found")
    }
    startCommand(getProject, "Test Intention Formatting") {
      CodeStyleManager.getInstance(getProject).reformat(myFixture.getFile)
      myFixture.checkResult(groom(resultText))
    }
  }

  def checkIntentionIsNotAvailable(text: String, familyName: String = this.familyName) {
    assert(intentionByFamilyName(text, familyName).isEmpty, "Intention is found")
  }

  def checkIntentionIsAvailable(text: String, familyName: String = this.familyName) {
    assert(intentionByFamilyName(text, familyName).isDefined, "Intention is not found")
  }


  def intentionByFamilyName(text: String, familyName: String): Option[IntentionAction] = {
    myFixture.configureByText(ScalaFileType.INSTANCE, groom(text))
    val intentions: util.List[IntentionAction] = myFixture.getAvailableIntentions
    intentions.find(action => action.getFamilyName == familyName)
  }

  protected def groom(text: String) = text.stripMargin.replace("\r", "").trim

  protected def getScalaCodeStyleSettings =
    CodeStyleSettingsManager.getSettings(getProject).getCustomSettings(classOf[ScalaCodeStyleSettings])
}

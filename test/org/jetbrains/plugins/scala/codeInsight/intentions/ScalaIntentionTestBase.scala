package org.jetbrains.plugins.scala
package codeInsight.intentions

import com.intellij.codeInsight.intention.IntentionAction
import org.junit.Assert
import java.util
import extensions._
import scala.Some
import com.intellij.psi.codeStyle.CodeStyleManager
import base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * @author Ksenia.Sautina
 * @since 4/11/12
 */

abstract class ScalaIntentionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  def familyName: String

  def doTest(text: String, resultText: String, familyName: String = this.familyName) {
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, text)
    val intentions: util.List[IntentionAction] = myFixture.getAvailableIntentions
    assert(!intentions.isEmpty)
    import scala.collection.JavaConversions._
    intentions.find(action => action.getFamilyName == familyName) match {
      case Some(action) => action.invoke(myFixture.getProject, myFixture.getEditor, myFixture.getFile)
      case None => Assert.fail("Intention is not found")
    }
    inWriteAction {
      CodeStyleManager.getInstance(getProject).reformat(myFixture.getFile)
      myFixture.checkResult(resultText)
    }
  }

  def checkIntentionIsNotAvailable(text: String, familyName: String = this.familyName) {
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, text)
    val intentions: util.List[IntentionAction] = myFixture.getAvailableIntentions

    import scala.collection.JavaConversions._
    assert(intentions.filter(action => action.getFamilyName == familyName).isEmpty)
  }

}

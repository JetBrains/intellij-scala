package org.jetbrains.plugins.scala
package codeInsight.intentions

import lang.completion3.ScalaLightCodeInsightFixtureTestAdapter
import com.intellij.codeInsight.intention.IntentionAction
import org.junit.Assert
import java.util.List

/**
 * @author Ksenia.Sautina
 * @since 4/11/12
 */

abstract class ScalaIntentionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  def familyName: String

  def doTest(text: String, resultText: String, familyName: String = this.familyName) {
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, text)
    val intentions: List[IntentionAction] = myFixture.getAvailableIntentions
    assert(!intentions.isEmpty)
    import scala.collection.JavaConversions._
    intentions.find(action => action.getFamilyName == familyName) match {
      case Some(action) => action.invoke(myFixture.getProject, myFixture.getEditor, myFixture.getFile)
      case None => Assert.fail("Intention is not found")
    }
    myFixture.checkResult(resultText)
  }

}

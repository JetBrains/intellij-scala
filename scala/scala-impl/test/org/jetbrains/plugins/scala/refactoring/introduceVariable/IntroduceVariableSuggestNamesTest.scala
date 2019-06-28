package org.jetbrains.plugins.scala.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class IntroduceVariableSuggestNamesTest extends AbstractIntroduceVariableValidatorTestBase("suggestNames") {

  override protected def doTest(replaceAllOccurrences: Boolean, fileText: String): String = {
    val startOffset = myEditor.getSelectionModel.getSelectionStart
    val endOffset = myEditor.getSelectionModel.getSelectionEnd
    new ScalaIntroduceVariableHandler()
      .suggestedNamesForExpression(myFile, startOffset, endOffset)(myFile.getProject, myEditor)
      .mkString("\n")
  }

  protected def getName(fileText: String): String = ???
}

object IntroduceVariableSuggestNamesTest {
  def suite() = new IntroduceVariableSuggestNamesTest
}
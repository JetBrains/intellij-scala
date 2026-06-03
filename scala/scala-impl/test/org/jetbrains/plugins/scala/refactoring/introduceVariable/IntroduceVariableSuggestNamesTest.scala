package org.jetbrains.plugins.scala.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler

class IntroduceVariableSuggestNamesTest extends AbstractIntroduceVariableValidatorTestBase("suggestNames") {
  override protected def getName(fileText: String): String = ""

  override protected def doTest(
    replaceAllOccurrences: Boolean,
    fileText: String
  ): String = {
    val selectionModel = fixture.editor.getSelectionModel
    val startOffset = selectionModel.getSelectionStart
    val endOffset = selectionModel.getSelectionEnd
    val handler = new ScalaIntroduceVariableHandler()
    val names = handler.suggestedNamesForExpression(fixture.psiFile, startOffset, endOffset)(fixture.psiFile.getProject, fixture.editor)
    names.mkString("\n")
  }
}

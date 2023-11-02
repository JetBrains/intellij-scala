package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.openapi.project.Project
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler

class IntroduceVariableSuggestNamesTest extends TestCase

object IntroduceVariableSuggestNamesTest {
  def suite(): Test = new AbstractIntroduceVariableValidatorTestBase("suggestNames") {
    override protected def needsSdk: Boolean = true

    override protected def getName(fileText: String): String = ???

    override protected def doTest(
      replaceAllOccurrences: Boolean,
      fileText: String,
      project: Project
    ): String = {
      val selectionModel = myFixture.editor.getSelectionModel
      val startOffset = selectionModel.getSelectionStart
      val endOffset = selectionModel.getSelectionEnd
      val handler = new ScalaIntroduceVariableHandler()
      val names = handler.suggestedNamesForExpression(myFixture.psiFile, startOffset, endOffset)(myFixture.psiFile.getProject, myFixture.editor)
      names.mkString("\n")
    }
  }
}
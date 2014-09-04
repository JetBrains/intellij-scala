package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * Nikolay.Tropin
 * 8/23/13
 */
abstract class ScalaGenerateTestBase extends ScalaLightCodeInsightFixtureTestAdapter{
  val handler: LanguageCodeInsightActionHandler

  def testInvoke(text: String, assumedText: String, checkCaret: Boolean): Unit = {
    val (nText, nResult) = (text.stripMargin.replace("\r", "").trim, assumedText.stripMargin.replace("\r", "").trim)
    val caretIndex = nText.indexOf(CARET_MARKER)
    myFixture.configureByText("dummy.scala", nText.replace(CARET_MARKER, ""))
    val caretModel = myFixture.getEditor.getCaretModel
    caretModel.moveToOffset(caretIndex)
    val file: PsiFile = myFixture.getFile
    extensions.startCommand(getProject, "Generate Action Test") {
      handler.invoke(getProject, myFixture.getEditor, file)
    }
    if (checkCaret) {
      val resultCaretIndex = nResult.indexOf(CARET_MARKER)
      val actualCaretIndex = caretModel.getOffset
      assert(resultCaretIndex == actualCaretIndex, "Wrong caret position after generating")
    }
    myFixture.checkResult(nResult.replace(CARET_MARKER, ""), true)
  }

  def checkIsAvailable(text: String, assumedResult: Boolean = true): Unit = {
    val caretIndex = text.indexOf(CARET_MARKER)
    myFixture.configureByText("dummy.scala", text.replace(CARET_MARKER, ""))
    myFixture.getEditor.getCaretModel.moveToOffset(caretIndex)

    val file: PsiFile = myFixture.getFile
    val message = s"Generate companion object is${if (assumedResult) " not" else ""} available"
    assert(handler.isValidFor(myFixture.getEditor, file) == assumedResult, message)
  }

  def checkIsNotAvailable(text: String) = checkIsAvailable(text, assumedResult = false)
}

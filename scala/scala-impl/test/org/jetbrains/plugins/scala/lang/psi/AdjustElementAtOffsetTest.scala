package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class AdjustElementAtOffsetTest extends ScalaLightCodeInsightFixtureTestCase {
  private val space = " "
  private val caret = "<caret>"
  private sealed class SwitchesTriBool
  private object OnlyWhenPreferIdentifier extends SwitchesTriBool
  private object True extends SwitchesTriBool
  private object False extends SwitchesTriBool

  private def doTest(code: String)(switchesToPrevious: SwitchesTriBool): Unit = {
    val file = myFixture.configureByText("dummy.scala", code)
    val caretOffset = myFixture.getCaretOffset
    val element = file.findElementAt(caretOffset)
    val prev = PsiTreeUtil.prevLeaf(element)

    testWith(preferIdentifier = true, switchesToPrevious = switchesToPrevious == True || switchesToPrevious == OnlyWhenPreferIdentifier)
    testWith(preferIdentifier = false, switchesToPrevious = switchesToPrevious == True)

    def testWith(preferIdentifier: Boolean, switchesToPrevious: Boolean): Unit = {
      val selected = ScalaPsiUtil.adjustElementAtOffset(element, caretOffset, preferIdentifier)
      if (switchesToPrevious) {
        assert(prev == selected, s"'${prev.getText}' != '${selected.getText}' ($prev != $selected)")
      } else {
        assert(element == selected, s"'${element.getText}' != '${selected.getText}' ($element != $selected)")
      }
    }
  }

  def test_identifier_before_ws(): Unit = doTest(
    s"identifier$caret$space"
  )(switchesToPrevious = True)

  def test_caret_between_ws(): Unit = doTest(
    s"identifier$space$caret$space"
  )(switchesToPrevious = False)

  def test_op_before_space(): Unit = doTest(
    s"+$caret$space"
  )(switchesToPrevious = True)

  def test_ws_before_identifier(): Unit = doTest(
    s"identifier1$space${caret}identifier2"
  )(switchesToPrevious = False)

  def test_identifier_before_op(): Unit = doTest(
    s"identifier$caret+"
  )(switchesToPrevious = OnlyWhenPreferIdentifier)
}

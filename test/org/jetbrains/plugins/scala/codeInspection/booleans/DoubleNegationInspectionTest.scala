package org.jetbrains.plugins.scala
package codeInspection.booleans

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * Nikolay.Tropin
 * 4/24/13
 */
class DoubleNegationInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  val annotation = "Double negation"
  val hint = "Remove double negation"

  private def check(text: String) {
    checkTextHasError(text, annotation, classOf[DoubleNegationInspection])
  }

  private def testFix(text: String, result: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), hint, classOf[DoubleNegationInspection])
  }

  def test_NotNotTrue() {
    val selectedText = s"$START!(!true)$END"
    check(selectedText)

    val text = "!(!true)"
    val result = "true"
    testFix(text, result)
  }

  def test_Not_ANotEqualsB() {
    val selectedText = s"val flag: Boolean = $START!(a != b)$END"
    check(selectedText)

    val text = "val flag: Boolean = !(a != b)"
    val result = "val flag: Boolean = a == b"
    testFix(text, result)
  }

  def test_NotA_NotEquals_B() {
    val selectedText = s"if ($START!a != b$END) true else false"
    check(selectedText)

    val text = "if (!a != b) true else false"
    val result = "if (a == b) true else false"
    testFix(text, result)
  }

  def test_NotA_Equals_NotB() {
    val selectedText = s"$START!a == !b$END"
    check(selectedText)

    val text = "!a == !b"
    val result = "a == b"
    testFix(text, result)
  }

  def test_NotA_NotEquals_NotB() {
    val selectedText = s"$START!a != !b$END"
    check(selectedText)

    val text = "!a != !b"
    val result = "a != b"
    testFix(text, result)
  }

}

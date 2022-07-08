package org.jetbrains.plugins.scala
package codeInspection.booleans

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class DoubleNegationInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[DoubleNegationInspection]
  override protected val description: String = "Double negation"

  private val hint = "Remove double negation"
  
  def test_NotNotTrue(): Unit = {
    val selectedText = s"$START!(!true)$END"
    checkTextHasError(selectedText)

    val text = "!(!true)"
    val result = "true"
    testQuickFix(text, result, hint)
  }

  def test_Not_ANotEqualsB(): Unit = {
    val selectedText = s"val flag: Boolean = $START!(a != b)$END"
    checkTextHasError(selectedText)

    val text = "val flag: Boolean = !(a != b)"
    val result = "val flag: Boolean = a == b"
    testQuickFix(text, result, hint)
  }

  def test_NotA_NotEquals_B(): Unit = {
    val selectedText = s"if ($START!a != b$END) true else false"
    checkTextHasError(selectedText)

    val text = "if (!a != b) true else false"
    val result = "if (a == b) true else false"
    testQuickFix(text, result, hint)
  }

  def test_NotA_Equals_NotB(): Unit = {
    val selectedText = s"$START!a == !b$END"
    checkTextHasError(selectedText)

    val text = "!a == !b"
    val result = "a == b"
    testQuickFix(text, result, hint)
  }

  def test_NotA_NotEquals_NotB(): Unit = {
    val selectedText = s"$START!a != !b$END"
    checkTextHasError(selectedText)

    val text = "!a != !b"
    val result = "a != b"
    testQuickFix(text, result, hint)
  }
}

package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.testFramework.EditorTestUtil

/**
 * Nikolay.Tropin
 * 2014-05-08
 */
class GetGetOrElseTest extends OperationsOnCollectionInspectionTest {

  override val hint = ScalaInspectionBundle.message("get.getOrElse.hint")
  override val classOfInspection = classOf[GetGetOrElseInspection]

  def test_1(): Unit = {
    val selected = s"""Map().${START}get(0).getOrElse("")$END"""
    checkTextHasError(selected)
    val text = "Map().get(0).getOrElse(\"\")"
    val result = "Map().getOrElse(0, \"\")"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = s"""Map("a" -> "A") ${START}get "b" getOrElse "B"$END"""
    checkTextHasError(selected)
    val text = """Map("a" -> "A") get "b" getOrElse "B""""
    val result = """Map("a" -> "A").getOrElse("b", "B")"""
    testQuickFix(text, result, hint)
  }
}

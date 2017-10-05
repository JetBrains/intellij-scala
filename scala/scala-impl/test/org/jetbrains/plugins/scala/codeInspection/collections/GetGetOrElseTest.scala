package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-08
 */
class GetGetOrElseTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  val hint = InspectionBundle.message("get.getOrElse.hint")
  override val classOfInspection = classOf[GetGetOrElseInspection]

  def test_1() {
    val selected = s"""Map().${START}get(0).getOrElse("")$END"""
    checkTextHasError(selected)
    val text = "Map().get(0).getOrElse(\"\")"
    val result = "Map().getOrElse(0, \"\")"
    testQuickFix(text, result, hint)
  }

  def test_2() {
    val selected = s"""Map("a" -> "A") ${START}get "b" getOrElse "B"$END"""
    checkTextHasError(selected)
    val text = """Map("a" -> "A") get "b" getOrElse "B""""
    val result = """Map("a" -> "A").getOrElse("b", "B")"""
    testQuickFix(text, result, hint)
  }
}

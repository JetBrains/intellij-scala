package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class FilterSizeTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("filter.size.hint")
  def test_1() {
    val selected = s"Array().${START}filter(x => true).size$END"
    checkTextHasError(selected)
    val text = "Array().filter(x => true).size"
    val result = "Array().count(x => true)"
    testQuickFix(text, result, hint)
  }

  def test_2() {
    val selected = s"List().${START}filter(x => true).length$END"
    checkTextHasError(selected)
    val text = "List().filter(x => true).length"
    val result = "List().count(x => true)"
    testQuickFix(text, result, hint)
  }

  def test_3() {
    val selected = s"Map() ${START}filter (x => true) size$END"
    checkTextHasError(selected)
    val text = "Map() filter (x => true) size"
    val result = "Map() count (x => true)"
    testQuickFix(text, result, hint)
  }

  def test_4() {
    val selected = s"List().${START}filter {x => true}.size$END"
    checkTextHasError(selected)
    val text =
      """List().filter {
        |  x => true
        |}.size
        |""".stripMargin
    val result =
      """List().count {
        |  x => true
        |}
        |""".stripMargin
    testQuickFix(text, result, hint)
  }


  override val classOfInspection = classOf[FilterSizeInspection]
}

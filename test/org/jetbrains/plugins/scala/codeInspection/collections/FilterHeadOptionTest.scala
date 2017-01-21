package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class FilterHeadOptionTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  val hint = InspectionBundle.message("filter.headOption.hint")
  def test_1() {
    val selected = s"List(0).${START}filter(x => true).headOption$END"
    checkTextHasError(selected)
    val text = "List(0).filter(x => true).headOption"
    val result = "List(0).find(x => true)"
    testQuickFix(text, result, hint)
  }

  def test_2() {
    val selected = s"(List(0) ${START}filter (x => true)).headOption$END"
    checkTextHasError(selected)
    val text = "(List(0) filter (x => true)).headOption"
    val result = "List(0) find (x => true)"
    testQuickFix(text, result, hint)
  }

  def test_3() {
    val selected = s"List(0).${START}filter(x => true).headOption$END.isDefined"
    checkTextHasError(selected)
    val text = "List(0).filter(x => true).headOption.isDefined"
    val result = "List(0).find(x => true).isDefined"
    testQuickFix(text, result, hint)
  }

  def testSideEffect(): Unit = {
    checkTextHasNoErrors(
      """
        |List(0, 1).filter { x =>
        |  println(x)
        |  true
        |}.headOption
      """.stripMargin)
  }

  override val classOfInspection = classOf[FilterHeadOptionInspection]
}

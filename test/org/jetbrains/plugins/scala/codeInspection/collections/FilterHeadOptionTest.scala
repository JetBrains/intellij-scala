package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class FilterHeadOptionTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("filter.headOption.hint")
  def test_1() {
    val selected = s"List(0).${START}filter(x => true).headOption$END"
    check(selected)
    val text = "List(0).filter(x => true).headOption"
    val result = "List(0).find(x => true)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"(List(0) ${START}filter (x => true)).headOption$END"
    check(selected)
    val text = "(List(0) filter (x => true)).headOption"
    val result = "List(0) find (x => true)"
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"List(0).${START}filter(x => true).headOption$END.isDefined"
    check(selected)
    val text = "List(0).filter(x => true).headOption.isDefined"
    val result = "List(0).find(x => true).isDefined"
    testFix(text, result, hint)
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

  override val inspectionClass = classOf[FilterHeadOptionInspection]
}

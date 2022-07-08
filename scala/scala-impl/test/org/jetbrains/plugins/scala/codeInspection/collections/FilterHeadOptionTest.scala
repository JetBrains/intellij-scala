package org.jetbrains.plugins.scala
package codeInspection
package collections

class FilterHeadOptionTest extends OperationsOnCollectionInspectionTest {

  override val hint = ScalaInspectionBundle.message("filter.headOption.hint")
  def test_1(): Unit = {
    val selected = s"List(0).${START}filter(x => true).headOption$END"
    checkTextHasError(selected)
    val text = "List(0).filter(x => true).headOption"
    val result = "List(0).find(x => true)"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = s"(List(0) ${START}filter (x => true)).headOption$END"
    checkTextHasError(selected)
    val text = "(List(0) filter (x => true)).headOption"
    val result = "List(0) find (x => true)"
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
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

package org.jetbrains.plugins.scala
package codeInspection
package collections

class FilterIsEmptyCheckTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[FilterEmptyCheckInspection]

  override protected val hint: String = ScalaInspectionBundle.message("filter.empty.check.hint")

  def test_1(): Unit = {
    val selected = s"(Map(1 -> 2)$START filter (x => true)).size == 0$END"
    checkTextHasError(selected)
    val text = "(Map(1 -> 2) filter (x => true)).size == 0"
    val result = "!(Map(1 -> 2) exists (x => true))"
    testQuickFix(text, result, hint)
  }

  def testNoError(): Unit = {
    val text = "Seq(0).filter(_ > 0).size == 1"
    checkTextHasNoErrors(text)
  }

  def testNoError2(): Unit = {
    val text = "Seq(0).filter(_ > 0).size + 1"
    checkTextHasNoErrors(text)
  }

  def testWithSideEffect(): Unit = {
    val text =
      """var z = 1
        |Seq(0).filter { x =>
        |  z = x
        |  true
        |}.size > 0
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testFilterEqualsNone(): Unit = {
    val selected = s"Option(1)$START.filter(x => true) == None$END"
    checkTextHasError(selected)
    val text = "Option(1).filter(x => true) == None"
    val result = "!Option(1).exists(x => true)"
    testQuickFix(text, result, hint)
  }

  def testWithHeadOption(): Unit = {
    val selected = s"Seq(1)$START.filter(x => true).headOption == None$END"
    checkTextHasError(selected)
    val text = "Seq(1).filter(x => true).headOption == None"
    val result = "!Seq(1).exists(x => true)"
    testQuickFix(text, result, hint)
  }

}

class FilterNonEmptyCheckTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[FilterEmptyCheckInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("filter.nonempty.check.hint")

  def testArraySizeGrZero(): Unit = {
    val selected = s"Array()$START.filter(x => true).size > 0$END"
    checkTextHasError(selected)
    val text = "Array().filter(x => true).size > 0"
    val result = "Array().exists(x => true)"
    testQuickFix(text, result, hint)
  }

  def testLenthgGrEqOne(): Unit = {
    val selected = s"List()$START.filter(x => true).length >= 1$END"
    checkTextHasError(selected)
    val text = "List().filter(x => true).length >= 1"
    val result = "List().exists(x => true)"
    testQuickFix(text, result, hint)
  }

  def testNonEmpty(): Unit = {
    val selected = s"List()$START.filter(x => true).nonEmpty$END"
    checkTextHasError(selected)
    val text = "List().filter(x => true).nonEmpty"
    val result = "List().exists(x => true)"
    testQuickFix(text, result, hint)
  }

  def testNoError(): Unit = {
    val text = "Seq(0).filter(_ > 0).size == 1"
    checkTextHasNoErrors(text)
  }

  def testFilterIsDefined(): Unit = {
    val selected = s"Option(1)$START filter (x => true) isDefined$END"
    checkTextHasError(selected)
    val text = "Option(1) filter (x => true) isDefined"
    val result = "Option(1) exists (x => true)"
    testQuickFix(text, result, hint)
  }

  def testWithLastOption(): Unit = {
    val selected = s"Seq(1)$START.filter(x => true).lastOption.isDefined$END"
    checkTextHasError(selected)
    val text = "Seq(1).filter(x => true).lastOption.isDefined"
    val result = "Seq(1).exists(x => true)"
    testQuickFix(text, result, hint)
  }

}

class FilterNotIsEmptyCheckTest extends OperationsOnCollectionInspectionTest {
  override val classOfInspection = classOf[FilterEmptyCheckInspection]
  override val hint = ScalaInspectionBundle.message("filterNot.empty.check.hint")

  def testFilterNotSizeEqZero(): Unit = {
    val selected = s"List()$START.filterNot(x => true).size == 0$END"
    checkTextHasError(selected)
    val text = "List().filterNot(x => true).size == 0"
    val result = "List().forall(x => true)"
    testQuickFix(text, result, hint)
  }

  def testFilterNotIsEmpty(): Unit = {
    val selected = s"List()$START.filterNot(x => true).isEmpty$END"
    checkTextHasError(selected)
    val text = "List().filterNot(x => true).isEmpty"
    val result = "List().forall(x => true)"
    testQuickFix(text, result, hint)
  }

  def testWithHeadOption(): Unit = {
    val selected = s"List()$START.filterNot(x => true).headOption.isEmpty$END"
    checkTextHasError(selected)
    val text = "List().filterNot(x => true).headOption.isEmpty"
    val result = "List().forall(x => true)"
    testQuickFix(text, result, hint)
  }

}

class FilterNotNonEmptyCheckTest extends OperationsOnCollectionInspectionTest {
  override val classOfInspection = classOf[FilterEmptyCheckInspection]
  override val hint = ScalaInspectionBundle.message("filterNot.nonempty.check.hint")

  def testFilterNotSizeGrZero(): Unit = {
    val selected = s"List()$START.filterNot(x => true).size > 0$END"
    checkTextHasError(selected)
    val text = "List().filterNot(x => true).size > 0"
    val result = "!List().forall(x => true)"
    testQuickFix(text, result, hint)
  }

  def testFilterNotNonEmpty(): Unit = {
    val selected = s"List()$START.filterNot(x => true).nonEmpty$END"
    checkTextHasError(selected)
    val text = "List().filterNot(x => true).nonEmpty"
    val result = "!List().forall(x => true)"
    testQuickFix(text, result, hint)
  }

  def testFilterNotIsDefined(): Unit = {
    val selected = s"Option(1)$START filterNot (x => true) isDefined$END"
    checkTextHasError(selected)
    val text = "Option(1) filterNot (x => true) isDefined"
    val result = "!(Option(1) forall (x => true))"
    testQuickFix(text, result, hint)
  }

  def testWithHeadOption(): Unit = {
    val selected = s"List()$START.filterNot(x => true).headOption != None$END"
    checkTextHasError(selected)
    val text = "List().filterNot(x => true).headOption != None"
    val result = "!List().forall(x => true)"
    testQuickFix(text, result, hint)
  }
}

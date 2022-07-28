package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class CheckEmptinessTest extends OperationsOnCollectionInspectionTest {
  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[EmptyCheckInspection]
}

class ReplaceIsEmptyTest extends CheckEmptinessTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.isEmpty")

  def testNotNonEmpty(): Unit = {
    val selected = s"$START!Seq().nonEmpty$END"
    checkTextHasError(selected)
    val text = "!Seq().nonEmpty"
    val result = "Seq().isEmpty"
    testQuickFix(text, result, hint)
  }

  def testNotIsDefined(): Unit = {
    val selected = s"$START!Option(1).isDefined$END"
    checkTextHasError(selected)
    val text = "!Option(1).isDefined"
    val result = "Option(1).isEmpty"
    testQuickFix(text, result, hint)
  }

  def testSizeEqualsZero(): Unit = {
    val selected = s"Seq()$START.size == 0$END"
    checkTextHasError(selected)
    val text = "Seq().size == 0"
    val result = "Seq().isEmpty"
    testQuickFix(text, result, hint)
  }

  def testEqualsNone(): Unit = {
    val selected = s"Option(1)$START == None$END"
    checkTextHasError(selected)
    val text = "Option(1) == None"
    val result = "Option(1).isEmpty"
    testQuickFix(text, result, hint)
  }

  def testNotSizeNotEqualsZero(): Unit = {
    val selected = s"$START!(Seq().size != 0)$END"
    checkTextHasError(selected)
    val text = "!(Seq().size != 0)"
    val result = "Seq().isEmpty"
    testQuickFix(text, result, hint)
  }

  def testWithHeadOption(): Unit = {
    val selected = s"Seq(1)$START.headOption == None$END"
    checkTextHasError(selected)
    val text = "Seq(1).headOption == None"
    val result = "Seq(1).isEmpty"
    testQuickFix(text, result, hint)
  }

  def testWithLastOption(): Unit = {
    val selected = s"$START!Seq(1).lastOption.isDefined$END"
    checkTextHasError(selected)
    val text = "!Seq(1).lastOption.isDefined"
    val result = "Seq(1).isEmpty"
    testQuickFix(text, result, hint)
  }
}

class ReplaceWithIsDefinedTest extends CheckEmptinessTest {
  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.isDefined")

  def testNotEqualsNone(): Unit = {
    val selected = s"Option(1)$START != None$END"
    checkTextHasError(selected)
    val text = "Option(1) != None"
    val result = "Option(1).isDefined"
    testQuickFix(text, result, hint)
  }

  def testNoneNotEquals(): Unit = {
    val selected = s"${START}None != ${END}Option(1)"
    checkTextHasError(selected)
    val text = "None != Option(1)"
    val result = "Option(1).isDefined"
    testQuickFix(text, result, hint)
  }

  def testExistTrue(): Unit = {
    val selected = s"Option(1)$START.exists(_ => true)$END"
    checkTextHasError(selected)
    val text = "Option(1).exists(_ => true)"
    val result = "Option(1).isDefined"
    testQuickFix(text, result, hint)
  }

  def testExistConstTrue2(): Unit = {
    val selected = s"Option()$START.exists(scala.Function.const(true))$END"
    checkTextHasError(selected)
    val text = "Option(1).exists(scala.Function.const(true))"
    val result = "Option(1).isDefined"
    testQuickFix(text, result, hint)
  }

}

class ReplaceWithNonEmptyTest extends CheckEmptinessTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.nonEmpty")

  def testNotIsEmpty(): Unit = {
    val selected = s"$START!Seq().isEmpty$END"
    checkTextHasError(selected)
    val text = "!Seq().isEmpty"
    val result = "Seq().nonEmpty"
    testQuickFix(text, result, hint)
  }

  def testSizeGreaterZero(): Unit = {
    val selected = s"Seq()$START.size > 0$END"
    checkTextHasError(selected)
    val text = "Seq().size > 0"
    val result = "Seq().nonEmpty"
    testQuickFix(text, result, hint)
  }

  def testLengthGrEqOne(): Unit = {
    val selected = s"Seq()$START.length >= 1$END"
    checkTextHasError(selected)
    val text = "Seq().size >= 1"
    val result = "Seq().nonEmpty"
    testQuickFix(text, result, hint)
  }

  def testSizeNotEqualsZero(): Unit = {
    val selected = s"Seq()$START.size != 0$END"
    checkTextHasError(selected)
    val text = "Seq().size != 0"
    val result = "Seq().nonEmpty"
    testQuickFix(text, result, hint)
  }

  def testExistTrue(): Unit = {
    val selected = s"Seq()$START.exists(_ => true)$END"
    checkTextHasError(selected)
    val text = "Seq().exists(_ => true)"
    val result = "Seq().nonEmpty"
    testQuickFix(text, result, hint)
  }

  def testExistConstTrue(): Unit = {
    val selected = s"import scala.Function.const; List()$START.exists(const(true))$END"
    checkTextHasError(selected)
    val text = "import scala.Function.const; List().exists(const(true))"
    val result = "import scala.Function.const; List().nonEmpty"
    testQuickFix(text, result, hint)
  }

  def testExistConstTrue2(): Unit = {
    val selected = s"Stream(1)$START.exists(scala.Function.const(true))$END"
    checkTextHasError(selected)
    val text = "Stream(1).exists(scala.Function.const(true))"
    val result = "Stream(1).nonEmpty"
    testQuickFix(text, result, hint)
  }

  // SCL-18080
  def testNonEmptyString(): Unit = {
    val string = "\"blub\""
    val selected = s"$START!$string.isEmpty$END"
    checkTextHasError(selected)
    val text = s"!$string.isEmpty"
    val result = s"$string.nonEmpty"
    testQuickFix(text, result, hint)
  }
}

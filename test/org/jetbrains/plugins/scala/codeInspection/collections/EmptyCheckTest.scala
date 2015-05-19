package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class EmptyCheckTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[EmptyCheckInspection]

  override def hint: String = isEmptyHint

  val isEmptyHint = InspectionBundle.message("replace.with.isEmpty")
  val isDefinedHint = InspectionBundle.message("replace.with.isDefined")
  val nonEmptyHint = InspectionBundle.message("replace.with.nonEmpty")

  def testNotIsEmpty() {
    val selected = s"$START!Seq().isEmpty$END"
    checkTextHasError(selected, nonEmptyHint, inspectionClass)
    val text = "!Seq().isEmpty"
    val result = "Seq().nonEmpty"
    testFix(text, result, nonEmptyHint)
  }

  def testNotNonEmpty() {
    val selected = s"$START!Seq().nonEmpty$END"
    checkTextHasError(selected, isEmptyHint, inspectionClass)
    val text = "!Seq().nonEmpty"
    val result = "Seq().isEmpty"
    testFix(text, result, isEmptyHint)
  }

  def testNotIsDefined() {
    val selected = s"$START!Option(1).isDefined$END"
    checkTextHasError(selected, isEmptyHint, inspectionClass)
    val text = "!Option(1).isDefined"
    val result = "Option(1).isEmpty"
    testFix(text, result, isEmptyHint)
  }

  def testSizeEqualsZero(): Unit = {
    val selected = s"Seq()$START.size == 0$END"
    checkTextHasError(selected, isEmptyHint, inspectionClass)
    val text = "Seq().size == 0"
    val result = "Seq().isEmpty"
    testFix(text, result, isEmptyHint)
  }

  def testSizeGreaterZero(): Unit = {
    val selected = s"Seq()$START.size > 0$END"
    checkTextHasError(selected, nonEmptyHint, inspectionClass)
    val text = "Seq().size > 0"
    val result = "Seq().nonEmpty"
    testFix(text, result, nonEmptyHint)
  }

  def testLengthGrEqOne(): Unit = {
    val selected = s"Seq()$START.length >= 1$END"
    checkTextHasError(selected, nonEmptyHint, inspectionClass)
    val text = "Seq().size >= 1"
    val result = "Seq().nonEmpty"
    testFix(text, result, nonEmptyHint)
  }

  def testEqualsNone(): Unit = {
    val selected = s"Option(1)$START == None$END"
    checkTextHasError(selected, isEmptyHint, inspectionClass)
    val text = "Option(1) == None"
    val result = "Option(1).isEmpty"
    testFix(text, result, isEmptyHint)
  }

  def testNotEqualsNone(): Unit = {
    val selected = s"Option(1)$START != None$END"
    checkTextHasError(selected, isDefinedHint, inspectionClass)
    val text = "Option(1) != None"
    val result = "Option(1).isDefined"
    testFix(text, result, isDefinedHint)
  }

  def testNoneNotEquals(): Unit = {
    val selected = s"${START}None != ${END}Option(1)"
    checkTextHasError(selected, isDefinedHint, inspectionClass)
    val text = "None != Option(1)"
    val result = "Option(1).isDefined"
    testFix(text, result, isDefinedHint)
  }

  def testSizeNotEqualsZero(): Unit = {
    val selected = s"Seq()$START.size != 0$END"
    checkTextHasError(selected, nonEmptyHint, inspectionClass)
    val text = "Seq().size != 0"
    val result = "Seq().nonEmpty"
    testFix(text, result, nonEmptyHint)
  }

  def testNotSizeNotEqualsZero(): Unit = {
    val selected = s"$START!(Seq().size != 0)$END"
    checkTextHasError(selected, isEmptyHint, inspectionClass)
    val text = "!(Seq().size != 0)"
    val result = "Seq().isEmpty"
    testFix(text, result, isEmptyHint)
  }

  def testWithHeadOption(): Unit = {
    val selected = s"Seq(1)$START.headOption == None$END"
    checkTextHasError(selected, isEmptyHint, inspectionClass)
    val text = "Seq(1).headOption == None"
    val result = "Seq(1).isEmpty"
    testFix(text, result, isEmptyHint)
  }

  def testWithLastOption(): Unit = {
    val selected = s"$START!Seq(1).lastOption.isDefined$END"
    checkTextHasError(selected, isEmptyHint, inspectionClass)
    val text = "!Seq(1).lastOption.isDefined"
    val result = "Seq(1).isEmpty"
    testFix(text, result, isEmptyHint)
  }
}

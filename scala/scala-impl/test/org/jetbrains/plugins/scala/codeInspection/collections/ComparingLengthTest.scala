package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class ComparingLengthTestBase extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[? <: OperationOnCollectionInspection] =
    classOf[ComparingLengthInspection]

  protected def resultSuffix(op: String): String

  def testLengthEqual(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}length == 2$END",
      "Seq(1, 2, 3).length == 2",
      s"Seq(1, 2, 3).${resultSuffix("==")}"
    )
  }

  def testLengthNotEqual(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}length != 2$END",
      "Seq(1, 2, 3).length != 2",
      s"Seq(1, 2, 3).${resultSuffix("!=")}"
    )
  }

  def testLengthLessThan(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}length < 2$END",
      "Seq(1, 2, 3).length < 2",
      s"Seq(1, 2, 3).${resultSuffix("<")}"
    )
  }

  def testLengthLessThanOrEqual(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}length <= 2$END",
      "Seq(1, 2, 3).length <= 2",
      s"Seq(1, 2, 3).${resultSuffix("<=")}"
    )
  }

  def testLengthGreaterThan(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}length > 2$END",
      "Seq(1, 2, 3).length > 2",
      s"Seq(1, 2, 3).${resultSuffix(">")}"
    )
  }

  def testLengthGreaterThanOrEqual(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}length >= 2$END",
      "Seq(1, 2, 3).length >= 2",
      s"Seq(1, 2, 3).${resultSuffix(">=")}"
    )
  }

  def testLengthIndexedSeq(): Unit = {
    checkTextHasNoErrors(
      "Vector(1, 2, 3).length == 2"
    )
  }

  def testLengthZero(): Unit = {
    checkTextHasNoErrors(
      "Seq(1, 2, 3).length == 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).length != 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).length > 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).length => 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).length < 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).length <= 0"
    )
  }

  def testSizeEqual(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}size == 2$END",
      "Seq(1, 2, 3).size == 2",
      s"Seq(1, 2, 3).${resultSuffix("==")}"
    )
  }

  def testSizeNotEqual(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}size != 2$END",
      "Seq(1, 2, 3).size != 2",
      s"Seq(1, 2, 3).${resultSuffix("!=")}"
    )
  }

  def testSizeLessThan(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}size < 2$END",
      "Seq(1, 2, 3).size < 2",
      s"Seq(1, 2, 3).${resultSuffix("<")}"
    )
  }

  def testSizeLessThanOrEqual(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}size <= 2$END",
      "Seq(1, 2, 3).size <= 2",
      s"Seq(1, 2, 3).${resultSuffix("<=")}"
    )
  }

  def testSizeGreaterThan(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}size > 2$END",
      "Seq(1, 2, 3).size > 2",
      s"Seq(1, 2, 3).${resultSuffix(">")}"
    )
  }

  def testSizeGreaterThanOEqual(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}size >= 2$END",
      "Seq(1, 2, 3).size >= 2",
      s"Seq(1, 2, 3).${resultSuffix(">=")}"
    )
  }

  def testSizeZero(): Unit = {
    checkTextHasNoErrors(
      "Seq(1, 2, 3).size == 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).size != 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).size > 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).size => 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).size < 0"
    )
    checkTextHasNoErrors(
      "Seq(1, 2, 3).size <= 0"
    )
  }

  def testSizeIndexedSeq(): Unit = {
    checkTextHasNoErrors(
      "Vector(1, 2, 3).size == 2"
    )
  }
}

class ComparingLengthTest_withoutSizeIs extends ComparingLengthTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_12

  override protected val hint: String = ScalaInspectionBundle.message("replace.with.lengthCompare")

  override protected def resultSuffix(op: String): String = s"lengthCompare(2) $op 0"
}

class ComparingLengthTest_withSizeIs extends ComparingLengthTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  override protected val hint: String = ScalaInspectionBundle.message("replace.with.sizeIs")

  override protected def resultSuffix(op: String): String = s"sizeIs $op 2"
}
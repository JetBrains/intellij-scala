package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.codeInspection.collections.ComparingDiffCollectionKinds._

abstract class ComparingDiffCollectionKindsInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ComparingDiffCollectionKindsInspection]

  protected val side: Side

  protected val toCollection: String

  override protected final lazy val hint: String =
    convertHint(side, toCollection)
}

class SeqToSetInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Left
  override protected val toCollection: String = "Set"

  def test(): Unit =
    doTest(s"Seq(1) $START==$END Set(1)",
      "Seq(1) == Set(1)",
      "Seq(1).toSet == Set(1)")
}

class SetToSeqInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Right
  override protected val toCollection: String = "Seq"

  def test(): Unit =
    doTest(s"Seq(1) $START==$END Set(1)",
      "Seq(1) == Set(1)",
      "Seq(1) == Set(1).toSeq")
}

class SeqToIteratorInspectionTest_2_12 extends ComparingDiffCollectionKindsInspectionTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_12
  override protected val side: Side = Side.Left
  override protected val toCollection: String = "Iterator"

  def test(): Unit =
    doTest(s"Seq(1) ++ Seq(2) $START==$END Iterator(1)",
      "Seq(1) ++ Seq(2) == Iterator(1)",
      "(Seq(1) ++ Seq(2)).toIterator == Iterator(1)")
}

class SeqToIteratorInspectionTest_2_13 extends ComparingDiffCollectionKindsInspectionTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version > LatestScalaVersions.Scala_2_12
  override protected val side: Side = Side.Left
  override protected val toCollection: String = "Iterator"

  def test(): Unit =
    doTest(s"Seq(1) ++ Seq(2) $START==$END Iterator(1)",
      "Seq(1) ++ Seq(2) == Iterator(1)",
      "(Seq(1) ++ Seq(2)).iterator == Iterator(1)")
}

class IteratorToSeqInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Right
  override protected val toCollection: String = "Seq"

  def test(): Unit =
    doTest(s"Seq(1) ++ Seq(2) $START==$END Iterator(1)",
      "Seq(1) ++ Seq(2) == Iterator(1)",
      "Seq(1) ++ Seq(2) == Iterator(1).toSeq")
}

class SeqToMapInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Left
  override protected val toCollection: String = "Map"

  def test(): Unit =
    doTest(s"Seq((1, 2)) $START!=$END Map(1 -> 2)",
      "Seq((1, 2)) != Map(1 -> 2)",
      "Seq((1, 2)).toMap != Map(1 -> 2)")
}

class MapToSeqInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Right
  override protected val toCollection: String = "Seq"

  def test(): Unit =
    doTest(s"Seq((1, 2)) $START!=$END Map(1 -> 2)",
      "Seq((1, 2)) != Map(1 -> 2)",
      "Seq((1, 2)) != Map(1 -> 2).toSeq")
}

class SeqToArrayInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Left
  override protected val toCollection: String = "Array"

  def test(): Unit =
    checkTextHasNoErrors(s"Seq(1).${START}equals$END(Array(1))")
}

class ArrayToSeqInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Right
  override protected val toCollection: String = "Seq"

  def test(): Unit =
    doTest(s"Seq(1).${START}equals$END(Array(1))",
      "Seq(1).equals(Array(1))",
      "Seq(1).equals(Array(1).toSeq)")
}

class SeqToSeqLeftInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Left
  override protected val toCollection: String = "Seq"

  def test(): Unit =
    checkTextHasNoErrors("Seq(1) == Seq(1)")
}

class SeqToSeqRightInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Right
  override protected val toCollection: String = "Seq"

  def test(): Unit =
    checkTextHasNoErrors("Seq(1) == Seq(1)")

}

class ToNullInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Left
  override protected val toCollection: String = null

  def test(): Unit =
    checkTextHasNoErrors("Set(1) == null")
}

class ToNothingInspectionTest extends ComparingDiffCollectionKindsInspectionTest {

  override protected val side: Side = Side.Left
  override protected val toCollection: String = null

  def test(): Unit =
    checkTextHasNoErrors("Map(1 -> 2) == ???")
}

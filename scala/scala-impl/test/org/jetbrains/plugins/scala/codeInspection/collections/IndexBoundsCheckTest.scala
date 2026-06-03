package org.jetbrains.plugins.scala
package codeInspection
package collections

class IndexBoundsCheckTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[IndexBoundsCheckInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("ifstmt.to.lift")

  def testSeqLess(): Unit = {
    doTest(
      s"val seq = Seq(1, 2, 3); val i = 1; val s = ${START}if (i < seq.length) Some(seq(i)) else None$END",
      "val seq = Seq(1, 2, 3); val i = 1; val s = if (i < seq.length) Some(seq(i)) else None",
      "val seq = Seq(1, 2, 3); val i = 1; val s = seq.lift(i)"
    )
  }

  def testListConstLess(): Unit = {
    doTest(
      s"val list = List(1, 2, 3); val s = ${START}if (1 < list.length) Some(list(1)) else None$END",
      "val list = List(1, 2, 3); val s = if (1 < list.length) Some(list(1)) else None",
      "val list = List(1, 2, 3); val s = list.lift(1)"
    )
  }

  def testVectorGreater(): Unit = {
    doTest(
      s"val vec = Vector(1, 2, 3); val i = 1; val s = ${START}if (vec.length > i) Some(vec(i)) else None$END",
      "val vec = Vector(1, 2, 3); val i = 1; val s = if (vec.length > i) Some(vec(i)) else None",
      "val vec = Vector(1, 2, 3); val i = 1; val s = vec.lift(i)"
    )
  }

  def testStreamLessElse(): Unit = {
    doTest(
      s"val s = Stream(1, 2, 3); val i = 1; val res = ${START}if (s.length <= i) None else Some(s(i))$END",
      "val s = Stream(1, 2, 3); val i = 1; val res = if (s.length <= i) None else Some(s(i))",
      "val s = Stream(1, 2, 3); val i = 1; val res = s.lift(i)"
    )
  }

  def testRangeConstantGreaterElse(): Unit = {
    doTest(
      s"val rng = Range(1, 2, 3); val s = ${START}if (1 >= rng.length) None else Some(rng(1))$END",
      "val rng = Range(1, 2, 3); val s = if (1 >= rng.length) None else Some(rng(1))",
      "val rng = Range(1, 2, 3); val s = rng.lift(1)"
    )
  }
}

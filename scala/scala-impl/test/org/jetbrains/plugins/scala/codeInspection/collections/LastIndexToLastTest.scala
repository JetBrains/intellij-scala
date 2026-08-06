package org.jetbrains.plugins.scala
package codeInspection
package collections

class LastIndexToLastTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[LastIndexToLastInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.last")

  def testExplicitApply(): Unit = {
    doTest(
      s"""
         |val seq = Seq(1, 2)
         |seq.${START}apply(seq.size - 1)$END
       """.stripMargin,
      """
        |val seq = Seq(1, 2)
        |seq.apply(seq.size - 1)
      """.stripMargin,
      """
        |val seq = Seq(1, 2)
        |seq.last
      """.stripMargin)
  }

  def testImplicitApply(): Unit = {
    doTest(
      s"""
         |val seq = Seq(1, 2)
         |seq$START(seq.size - 1)$END
       """.stripMargin,
      """
        |val seq = Seq(1, 2)
        |seq(seq.size - 1)
      """.stripMargin,
      """
        |val seq = Seq(1, 2)
        |seq.last
      """.stripMargin)
  }

  def testLength(): Unit = {
    doTest(
      s"""
         |val seq = Seq(1, 2)
         |seq$START(seq.length - 1)$END
       """.stripMargin,
      """
        |val seq = Seq(1, 2)
        |seq(seq.length - 1)
      """.stripMargin,
      """
        |val seq = Seq(1, 2)
        |seq.last
      """.stripMargin)
  }

  def testNotSeq(): Unit = {
    checkTextHasNoErrors(
      """
        |val set = Set(1, 2)
        |set(set.size - 1)
      """.stripMargin)
  }

  def testDiffSeqs(): Unit = {
    checkTextHasNoErrors(
      """
        |val seq = Seq(1, 2)
        |val seq2 = Seq(1, 2)
        |seq(seq2.size - 1)
      """.stripMargin)
  }

  def testNotLast(): Unit = {
    checkTextHasNoErrors(
      """
        |val seq = Seq(1, 2)
        |seq(seq.size - 2)
      """.stripMargin)
  }

  def testIndexedSeq(): Unit = {
    checkTextHasNoErrors(
      """val seq = scala.collection.IndexedSeq(1, 2)
        |seq(seq.size - 1)""".stripMargin)
    checkTextHasNoErrors(
      """import scala.collection.immutable.Vector
        |val v = Vector(1, 2)
        |v(v.length - 1)""".stripMargin)
    checkTextHasNoErrors(
      """val buf = scala.collection.mutable.ArrayBuffer(1, 2)
        |buf(buf.size - 1)""".stripMargin)
  }

}

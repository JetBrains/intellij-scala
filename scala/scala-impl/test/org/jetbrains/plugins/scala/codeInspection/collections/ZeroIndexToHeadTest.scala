package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class ZeroIndexToHeadTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ZeroIndexToHeadInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.head")

  def testApply(): Unit = {
    doTest(s"List(1, 2).${START}apply(0)$END",
      "List(1, 2).apply(0)",
      "List(1, 2).head")
    doTest(s"Seq(1, 2).${START}apply(0)$END",
      "Seq(1, 2).apply(0)",
      "Seq(1, 2).head")
  }

  def testBraces(): Unit = {
    doTest(s"List(1, 2)$START(0)$END",
      "List(1, 2)(0)",
      "List(1, 2).head")
    doTest(s"val seq = Seq(1, 2); seq$START(0)$END",
      "val seq = Seq(1, 2); seq(0)",
      "val seq = Seq(1, 2); seq.head")
    doTest(s"val arr = Array(Seq(1, 2)); arr(0)$START(0)$END",
      "val arr = Array(Seq(1, 2)); arr(0)(0)",
      "val arr = Array(Seq(1, 2)); arr(0).head")
  }

  def testIndexedSeq(): Unit = {
    checkTextHasNoErrors("scala.collection.IndexedSeq(1, 2)(0)")
    checkTextHasNoErrors(
      """import scala.collection.immutable.Vector
        |Vector(1, 2)(0)""".stripMargin)
    checkTextHasNoErrors("scala.collection.mutable.ArrayBuffer(1, 2)(0)")
  }
}

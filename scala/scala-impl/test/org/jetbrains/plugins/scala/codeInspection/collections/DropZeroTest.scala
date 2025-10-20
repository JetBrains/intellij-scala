package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class DropZeroTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[DropZeroInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("drop.with.0.is.redundant")

  def testDropZeroOnList(): Unit = {
    doTest(
      s"println(List(1, 2, 3).${START}drop(0)$END",
      "println(List(1, 2, 3).drop(0))",
      "println(List(1, 2, 3))"
    )
  }

  def testDropZeroOnSeq(): Unit = {
    doTest(
      s"println(Seq(1, 2, 3).${START}drop(0)$END",
      "println(Seq(1, 2, 3).drop(0))",
      "println(Seq(1, 2, 3))"
    )
  }
}

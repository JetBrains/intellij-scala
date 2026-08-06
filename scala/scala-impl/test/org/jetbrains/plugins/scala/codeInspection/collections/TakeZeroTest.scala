package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class TakeZeroTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[TakeZeroInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("take.0.is.always.empty")

  def testTakeZeroOnList(): Unit = {
    checkTextHasError(s"println(${START}List(1, 2, 3).take(0)$END)")
  }

  def testTakeZeroOnSeq(): Unit = {
    checkTextHasError(s"println(${START}Seq(1, 2, 3).take(0)$END)")
  }
}

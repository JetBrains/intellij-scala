package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class ZeroIndexToHeadTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[ZeroIndexToHeadInspection]

  override def hint: String = InspectionBundle.message("replace.with.head")

  def testApply(): Unit = {
    doTest(s"List(1, 2).${START}apply(0)$END",
      "List(1, 2).apply(0)",
      "List(1, 2).head")
    doTest(s"Seq(1, 2).${START}apply(0)$END",
      "Seq(1, 2).apply(0)",
      "Seq(1, 2).head")
  }

  def testBraces() {
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
}

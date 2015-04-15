package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class ReverseTakeReverseTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[ReverseTakeReverseInspection]

  override def hint: String = InspectionBundle.message("replace.reverse.take.reverse.with.takeRight")

  def test1(): Unit = {
    doTest(
      s"val n = 2; Seq().${START}reverse.take(n).reverse$END",
      "val n = 2; Seq().reverse.take(n).reverse",
      "val n = 2; Seq().takeRight(n)"
    )
  }
}

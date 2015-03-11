package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class DropTakeToSliceTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[DropTakeToSliceInspection]

  override def hint: String = InspectionBundle.message("replace.drop.take.with.slice")

  def testSeqWithLiteralArg(): Unit = {
    doTest(
      s"Seq().${START}drop(1).take(2)$END",
      "Seq().drop(1).take(2)",
      "Seq().slice(1, 3)"
    )
  }

  def testSeq(): Unit = {
    doTest(
      s"val n = 2; Seq().${START}drop(1).take(n)$END",
      "val n = 2; Seq().drop(1).take(n)",
      "val n = 2; Seq().slice(1, n + 1)"
    )
  }

  def testSeqWithInfixArg(): Unit = {
    doTest(
      s"val n = 2; Seq().${START}drop(1).take(n + 1)$END",
      "val n = 2; Seq().drop(1).take(n + 1)",
      "val n = 2; Seq().slice(1, n + 2)"
    )
  }

  def testSeqWithInfixArg2(): Unit = {
    doTest(
      s"val n = 2; Seq().${START}drop(1).take(1 + n)$END",
      "val n = 2; Seq().drop(1).take(1 + n)",
      "val n = 2; Seq().slice(1, 2 + n)"
    )
  }

  def testInfix(): Unit = {
    doTest(
      s"Seq() ${START}drop 1 take 2$END",
      "Seq() drop 1 take 2",
      "Seq().slice(1, 3)"
    )
  }
}

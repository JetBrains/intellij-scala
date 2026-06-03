package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class ReplaceWithSliceTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[DropTakeToSliceInspection]
}

class DropTakeToSliceTest extends ReplaceWithSliceTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.drop.take.with.slice")

  def testSeqWithLiteralArg(): Unit = {
    doTest(
      s"Seq().${START}drop(2).take(2)$END",
      "Seq().drop(2).take(2)",
      "Seq().slice(2, 4)"
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
      "val n = 2; Seq().slice(1, n + 2)"
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

class TakeDropToSliceTest extends ReplaceWithSliceTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.take.drop.with.slice")

  def testSeqWithLiteralArg(): Unit = {
    doTest(
      s"Seq().${START}take(4).drop(2)$END",
      "Seq().take(4).drop(2)",
      "Seq().slice(2, 4)"
    )
  }

  def testSeq(): Unit = {
    doTest(
      s"val n = 2; Seq().${START}take(n).drop(1)$END",
      "val n = 2; Seq().take(n).drop(1)",
      "val n = 2; Seq().slice(1, n)"
    )
  }

  def testSeqWithInfixArg(): Unit = {
    doTest(
      s"val n = 2; Seq().${START}take(n + 1).drop(1)$END",
      "val n = 2; Seq().take(n + 1).drop(1)",
      "val n = 2; Seq().slice(1, n + 1)"
    )
  }

  def testInfix(): Unit = {
    doTest(
      s"Seq() ${START}take 2 drop 1$END",
      "Seq() take 2 drop 1",
      "Seq().slice(1, 2)"
    )
  }
}

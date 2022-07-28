package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class UnzipSingleElementTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[UnzipSingleElementInspection]

  override protected val hint: String = ScalaInspectionBundle.message("replace.with.map")

  def testSeqUnzip_1(): Unit = {
    doTest(
      s"Seq((1, 2), (11, 22)).${START}unzip._1$END",
      "Seq((1, 2), (11, 22)).unzip._1",
      "Seq((1, 2), (11, 22)).map(_._1)"
    )
  }

  def testSeqUnzip_2(): Unit = {
    doTest(
      s"Seq((1, 2), (11, 22)).${START}unzip._2$END",
      "Seq((1, 2), (11, 22)).unzip._2",
      "Seq((1, 2), (11, 22)).map(_._2)"
    )
  }

  def testSeqUnzip3_1(): Unit = {
    doTest(
      s"Seq((1, 2, 3), (11, 22, 333)).${START}unzip3._1$END",
      "Seq((1, 2, 3), (11, 22, 333)).unzip3._1",
      "Seq((1, 2, 3), (11, 22, 333)).map(_._1)"
    )
  }

  def testSeqUnzip3_2(): Unit = {
    doTest(
      s"Seq((1, 2, 3), (11, 22, 333)).${START}unzip3._2$END",
      "Seq((1, 2, 3), (11, 22, 333)).unzip3._2",
      "Seq((1, 2, 3), (11, 22, 333)).map(_._2)"
    )
  }

  def testSeqUnzip3_(): Unit = {
    doTest(
      s"Seq((1, 2, 3), (11, 22, 333)).${START}unzip3._3$END",
      "Seq((1, 2, 3), (11, 22, 333)).unzip3._3",
      "Seq((1, 2, 3), (11, 22, 333)).map(_._3)"
    )
  }

  def testMap(): Unit = {
    doTest(
      s"""Map("key1" -> 1, "key2" -> 2).${START}unzip._1$END""",
      """Map("key1" -> 1, "key2" -> 2).unzip._1""",
      """Map("key1" -> 1, "key2" -> 2).map(_._1)"""
    )
  }

  def testOption(): Unit = {
    doTest(
      s"Option((1, 2)).${START}unzip._2$END",
      "Option((1, 2)).unzip._2",
      "Option((1, 2)).map(_._2)"
    )
  }

}

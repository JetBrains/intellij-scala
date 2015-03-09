package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class LastIndexToLastTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[LastIndexToLastInspection]

  override def hint: String = InspectionBundle.message("replace.with.last")

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
}

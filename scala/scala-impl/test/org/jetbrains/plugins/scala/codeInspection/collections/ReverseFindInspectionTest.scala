package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class ReverseFindInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ReverseFindInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.findlast")

  def testNormal(): Unit = {
    doTest(
      s"""
         |Seq(1, 2)$START.reverse.find(_ == 2)$END
       """.stripMargin,
      """
        |Seq(1, 2).reverse.find(_ == 2)
      """.stripMargin,
      """
        |Seq(1, 2).findLast(_ == 2)
      """.stripMargin)
  }

  def testBraced(): Unit = {
    doTest(
      s"""
         |Seq(1, 2)$START.reverse.find { x => true }$END
       """.stripMargin,
      """
        |Seq(1, 2).reverse.find { x => true }
      """.stripMargin,
      """
        |Seq(1, 2).findLast { x => true }
      """.stripMargin)
  }
}

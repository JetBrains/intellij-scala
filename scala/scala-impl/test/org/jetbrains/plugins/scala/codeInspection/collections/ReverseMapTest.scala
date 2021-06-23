package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class ReverseMapTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ReverseMapInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.reverse.map")

  def testNormal(): Unit = {
    doTest(
      s"""
         |Seq(1, 2).${START}reverse.map(a => a)$END
       """.stripMargin,
      """
        |Seq(1, 2).reverse.map(a => a)
      """.stripMargin,
      """
        |Seq(1, 2).reverseMap(a => a)
      """.stripMargin)
  }

  def testBraced(): Unit = {
    doTest(
      s"""
         |Seq(1, 2).${START}reverse.map { case a => a }$END
       """.stripMargin,
      """
        |Seq(1, 2).reverse.map { case a => a }
      """.stripMargin,
      """
        |Seq(1, 2).reverseMap { case a => a }
      """.stripMargin)
  }
}


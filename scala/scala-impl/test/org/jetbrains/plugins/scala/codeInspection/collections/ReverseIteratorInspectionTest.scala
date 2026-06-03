package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class ReverseIteratorInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ReverseIteratorInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.reverse.iterator")

  def testNormal(): Unit = {
    doTest(
      s"""
         |Seq(1, 2).${START}reverse.iterator$END
       """.stripMargin,
      """
        |Seq(1, 2).reverse.iterator
      """.stripMargin,
      """
        |Seq(1, 2).reverseIterator
      """.stripMargin)
  }
}

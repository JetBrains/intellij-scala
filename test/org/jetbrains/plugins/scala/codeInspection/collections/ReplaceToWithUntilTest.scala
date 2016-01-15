package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
  * @author Nikolay.Tropin
  */
class ReplaceToWithUntilTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[ReplaceToWithUntilInspection]

  override def hint: String = InspectionBundle.message("replace.to.with.until")

  def testInfix(): Unit = {
    doTest(
      s"""
         |val x = 42
         |0 ${START}to x - 1$END
       """.stripMargin,
      """
        |val x = 42
        |0 to x - 1
      """.stripMargin,
      """
        |val x = 42
        |0 until x
      """.stripMargin)
  }

  def testCall(): Unit = {
    doTest(
      s"""
         |val x = 42
         |0.${START}to(x - 1)$END
       """.stripMargin,
      """
        |val x = 42
        |0.to(x - 1)
      """.stripMargin,
      """
        |val x = 42
        |0.until(x)
      """.stripMargin)
  }

  def testOtherTo(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class A {
         |  def to(i: Int): Int = i
         |}
         |
         |val a = new A
         |val x = 42
         |a to x - 1
       """.stripMargin
      )
  }
}

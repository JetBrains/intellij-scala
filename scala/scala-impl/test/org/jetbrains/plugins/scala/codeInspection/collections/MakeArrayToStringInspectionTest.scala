package org.jetbrains.plugins.scala
package codeInspection
package collections

class MakeArrayToStringInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[MakeArrayToStringInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("format.with.mkstring")


  def testToString(): Unit = {
    doTest(
      s"""
         |Array(3).${START}toString$END
       """.stripMargin,
      """
        |Array(3).toString
      """.stripMargin,
      """
        |Array(3).mkString("Array(", ", ", ")")
      """.stripMargin)
  }


  def testPrint(): Unit = {
    doTest(
      s"""
         |println(${START}Array(3)$END)
       """.stripMargin,
      """
        |println(Array(3))
      """.stripMargin,
      """
        |println(Array(3).mkString("Array(", ", ", ")"))
      """.stripMargin)
  }

  def testSystemPrint(): Unit = {
    doTest(
      s"""
         |System.out.println(${START}Array(3)$END)
       """.stripMargin,
      """
        |System.out.println(Array(3))
      """.stripMargin,
      """
        |System.out.println(Array(3).mkString("Array(", ", ", ")"))
      """.stripMargin)
  }

  def testInterpolatedString(): Unit = {
    doTest(
      s"""
         |val a = Array(3)
         |s"before $$${START}a$END after"
       """.stripMargin,
      """
        |val a = Array(3)
        |s"before $a after"
      """.stripMargin,
      """
        |val a = Array(3)
        |s"before ${a.mkString("Array(", ", ", ")")} after"
      """.stripMargin)
  }

  def testBlockInInterpolatedString(): Unit = {
    doTest(
      s"""
         |val a = Array(3)
         |s"before $${${START}a$END} after"
       """.stripMargin,
      """
        |val a = Array(3)
        |s"before ${a} after"
      """.stripMargin,
      """
        |val a = Array(3)
        |s"before ${a.mkString("Array(", ", ", ")")} after"
      """.stripMargin)
  }

  def testAny2String(): Unit = {
    doTest(
      s"""
         |val a = Array(3)
         |${START}a$END + "test"
       """.stripMargin,
      """
        |val a = Array(3)
        |a + "test"
      """.stripMargin,
      """
        |val a = Array(3)
        |a.mkString("Array(", ", ", ")") + "test"
      """.stripMargin)
  }

  def testAddToString(): Unit = {
    doTest(
      s"""
         |val a = Array(3)
         |"test" + ${START}a$END
       """.stripMargin,
      """
        |val a = Array(3)
        |"test" + a
      """.stripMargin,
      """
        |val a = Array(3)
        |"test" + a.mkString("Array(", ", ", ")")
      """.stripMargin)
  }
}

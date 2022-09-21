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

  def testStringFormat(): Unit = {
    doTest(
      s"""
         |String.format("formatted: %s", ${START}Array(1)$END)
       """.stripMargin,
      """
        |String.format("formatted: %s", Array(1))
      """.stripMargin,
      """
        |String.format("formatted: %s", Array(1).mkString("Array(", ", ", ")"))
      """.stripMargin)
  }

  def testFormatOnString(): Unit = {
    doTest(
      s"""
         |"formatted: %s".format(${START}Array(1)$END)
       """.stripMargin,
      """
        |"formatted: %s".format(Array(1))
      """.stripMargin,
      """
        |"formatted: %s".format(Array(1).mkString("Array(", ", ", ")"))
      """.stripMargin)
  }

  def testFormattedOnString(): Unit = {
    doTest(
      s"""
         |"formatted: %s".formatted(${START}Array(1)$END)
       """.stripMargin,
      """
        |"formatted: %s".formatted(Array(1))
      """.stripMargin,
      """
        |"formatted: %s".formatted(Array(1).mkString("Array(", ", ", ")"))
      """.stripMargin)
  }

  def testAppendOnJavaStringBuilder(): Unit = {
    doTest(
      s"""
         |val b = new java.lang.StringBuilder
         |b.append(${START}Array(1)$END)
       """.stripMargin,
      """
        |val b = new java.lang.StringBuilder
        |b.append(Array(1))
      """.stripMargin,
      """
        |val b = new java.lang.StringBuilder
        |b.append(Array(1).mkString("Array(", ", ", ")"))
      """.stripMargin)
  }

  def testAppendOnScalaStringBuilder(): Unit = {
    doTest(
      s"""
         |val b = new scala.collection.mutable.StringBuilder
         |b.append(${START}Array(1)$END)
       """.stripMargin,
      """
        |val b = new scala.collection.mutable.StringBuilder
        |b.append(Array(1))
      """.stripMargin,
      """
        |val b = new scala.collection.mutable.StringBuilder
        |b.append(Array(1).mkString("Array(", ", ", ")"))
      """.stripMargin)
  }
}

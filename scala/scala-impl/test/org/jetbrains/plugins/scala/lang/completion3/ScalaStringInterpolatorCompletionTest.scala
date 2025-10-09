package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase

class ScalaStringInterpolatorCompletionTest extends ScalaCompletionTestBase {

  def testBracesBasic(): Unit = doCompletionTest(
    fileText =
      s"""
         |object A {
         |  val zzz = ""
         |  val y = "$$z$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object A {
         |  val zzz = ""
         |  val y = s"$$zzz$CARET"
         |}
      """.stripMargin,
    item = "zzz",
  )

  def testBracesBasicEmpty(): Unit = doCompletionTest(
    fileText =
      s"""
         |object A {
         |  val zzz = ""
         |  val y = "$$$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object A {
         |  val zzz = ""
         |  val y = s"$$zzz$CARET"
         |}
      """.stripMargin,
    item = "zzz",
  )

  def testBracesSingleLetter(): Unit = doCompletionTest(
    fileText =
      s"""
         |object A {
         |  val z = ""
         |  val y = "$$z$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object A {
         |  val z = ""
         |  val y = s"$$z$CARET"
         |}
      """.stripMargin,
    item = "z",
  )

  def testBracesSingleLetterEmpty(): Unit = doCompletionTest(
    fileText =
      s"""
         |object A {
         |  val z = ""
         |  val y = "$$$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object A {
         |  val z = ""
         |  val y = s"$$z$CARET"
         |}
      """.stripMargin,
    item = "z",
  )

  def testAddBracesWhenRequiredForMethods(): Unit = doCompletionTest(
    fileText =
      s"""
         |object A {
         |  def zzz(a: String): String = ""
         |  val y = "$$$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object A {
         |  def zzz(a: String): String = ""
         |  val y = s"$${zzz($CARET)}"
         |}
      """.stripMargin,
    item = "zzz",
  )
}

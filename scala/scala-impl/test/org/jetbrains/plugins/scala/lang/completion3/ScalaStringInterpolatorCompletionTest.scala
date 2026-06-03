package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.junit.Test

class ScalaStringInterpolatorCompletionTest extends ScalaCompletionTestBase {
  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

abstract class RemoveApplyIntentionTestBase extends ScalaIntentionTestBase {
  override val familyName = ScalaBundle.message("family.name.remove.unnecessary.apply")
}

class RemoveApplyIntentionTest extends RemoveApplyIntentionTestBase {

  def testRemoveApply(): Unit =
    doTest(
      s"val l = List.apply$CARET(1, 3, 4)",
      s"val l = List$CARET(1, 3, 4)"
    )

  def testRemoveApply2(): Unit =
    doTest(
      s"new AAAA().ap${CARET}ply(1)",
      s"new AAAA()$CARET(1)"
    )

  def testRemoveApply3(): Unit = {
    val text =
      s"""
         |object D {
         |  def foo() = B
         |
         |  foo.ap${CARET}ply(1)
         |}
      """.stripMargin
    val resultText =
      s"""
         |object D {
         |  def foo() = B
         |
         |  foo()$CARET(1)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def testRemoveApply4(): Unit = {
    val text =
      s"""
         |object D {
         |  def foo() = B
         |
         |  foo().ap${CARET}ply(1)
         |}
      """.stripMargin
    val resultText =
      s"""
         |object D {
         |  def foo() = B
         |
         |  foo()$CARET(1)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def testRemoveApply4_newLine(): Unit = {
    val text =
      s"""
         |object D {
         |  def foo() = B
         |
         |  foo()
         |    .ap${CARET}ply(1)
         |}
      """.stripMargin
    val resultText =
      s"""
         |object D {
         |  def foo() = B
         |
         |  foo()$CARET(1)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def testRemoveApply5(): Unit =
    doTest(
      s"(foo()).a${CARET}pply(1)",
      s"(foo())$CARET(1)"
    )

  def testRemoveApply6(): Unit =
    checkIntentionIsNotAvailable(
      s"""
         |object D {
         |  def foo()(implicit x: String) = B
         |  implicit val s: String = ""
         |  foo().${CARET}apply(1)
         |}
      """.stripMargin
    )

  def testRemoveApply7(): Unit =
    checkIntentionIsNotAvailable(
      s"""
         |object P {
         |  class AAAA()(implicit s: String) extends (Int => Int) {
         |    def this(x: Int) {
         |      this()
         |    }
         |    def apply(v1: Int) = v1 + 1
         |  }
         |  implicit val s: String = "text"
         |  (new AAAA()).ap${CARET}ply(1)
         |}
      """.stripMargin
    )

  def testRemoveApply8(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
         |object A {
         |  def foo = B
         |  def foo(x: Int) = B
         |  foo.a${CARET}pply(1)
         |}
      """.stripMargin
    )
  }

  // SCL-19193
  def testImplicitly(): Unit =
    checkIntentionIsNotAvailable(
      s"""
         |trait Result {
         |  def apply(): Unit = ()
         |}
         |def implicitly[T](implicit x: Int): Result = ???
         |
         |implicitly[Result].ap${CARET}ply()
         |""".stripMargin
    )
}

final class RemoveApplyIntentionTest_Scala3 extends RemoveApplyIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testFewerBracesSameLine(): Unit = doTest(
    text =
      s"""List(1, 2, 3).ap${CARET}ply:
         |  1""".stripMargin,
    resultText =
      s"""List(1, 2, 3)$CARET:
         |  1""".stripMargin,
  )

  def testFewerBracesNewLine(): Unit = doTest(
    text =
      s"""List(1, 2, 3)
         |  .ap${CARET}ply:
         |    1""".stripMargin,
    resultText =
      s"""List(1, 2, 3)$CARET:
         |  1""".stripMargin,
  )

  def testFewerBracesAfterFewerBraces(): Unit = checkIntentionIsNotAvailable(
    text =
      s"""List(1, 2, 3).map:
         |    _ + 2
         |  .ap${CARET}ply:
         |    1""".stripMargin
  )
}

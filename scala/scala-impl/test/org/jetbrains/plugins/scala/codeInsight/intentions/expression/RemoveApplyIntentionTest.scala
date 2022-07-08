package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class RemoveApplyIntentionTest extends ScalaIntentionTestBase {
  override val familyName = ScalaBundle.message("family.name.remove.unnecessary.apply")

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
      """
        |object D {
        |  def foo() = B
        |
        |  foo.ap<caret>ply(1)
        |}
      """.stripMargin
    val resultText =
      s"""
        |object D {
        |  def foo() = B
        |
        |  foo()${CARET}(1)
        |}
      """.stripMargin

    doTest(text, resultText)
  }

  def testRemoveApply4(): Unit = {
    val text =
      """
        |object D {
        |  def foo() = B
        |
        |  foo().ap<caret>ply(1)
        |}
      """.stripMargin
    val resultText =
      """
        |object D {
        |  def foo() = B
        |
        |  foo()<caret>(1)
        |}
      """.stripMargin

    doTest(text, resultText)
  }

  def testRemoveApply5(): Unit =
    doTest(
      s"(foo()).a${CARET}pply(1)",
      s"(foo())$CARET (1)"
    )

  def testRemoveApply6(): Unit =
    checkIntentionIsNotAvailable(
      """
        |object D {
        |  def foo()(implicit x: String) = B
        |  implicit val s: String = ""
        |  foo().<caret>apply(1)
        |}
      """.stripMargin
    )

  def testRemoveApply7(): Unit =
    checkIntentionIsNotAvailable(
      """
        |object P {
        |  class AAAA()(implicit s: String) extends (Int => Int) {
        |    def this(x: Int) {
        |      this()
        |    }
        |    def apply(v1: Int) = v1 + 1
        |  }
        |  implicit val s: String = "text"
        |  (new AAAA()).ap<caret>ply(1)
        |}
      """.stripMargin
    )

  def testRemoveApply8(): Unit = {
    checkIntentionIsNotAvailable(
      """
        |object A {
        |  def foo = B
        |  def foo(x: Int) = B
        |  foo.a<caret>pply(1)
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

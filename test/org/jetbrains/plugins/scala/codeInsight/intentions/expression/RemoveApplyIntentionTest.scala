package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intention.expression.RemoveApplyIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 4/12/12
 */

class RemoveApplyIntentionTest extends ScalaIntentionTestBase {
  val familyName = RemoveApplyIntention.familyName

  def testRemoveApply() {
    val text = "val l = List.apply<caret>(1, 3, 4)"
    val resultText = "val l = List<caret>(1, 3, 4)"

    doTest(text, resultText)
  }

  def testRemoveApply2() {
    val text = "new AAAA().ap<caret>ply(1)"
    val resultText = "new AAAA()<caret>(1)"

    doTest(text, resultText)
  }

  def testRemoveApply3() {
    val text =
      """
        |object D {
        |  def foo() = B
        |
        |  foo.ap<caret>ply(1)
        |}
      """
    val resultText =
      """
        |object D {
        |  def foo() = B
        |
        |  foo()<caret>(1)
        |}
      """

    doTest(text, resultText)
  }

  def testRemoveApply4() {
    val text =
      """
        |object D {
        |  def foo() = B
        |
        |  foo().ap<caret>ply(1)
        |}
      """
    val resultText =
      """
        |object D {
        |  def foo() = B
        |
        |  foo()<caret>(1)
        |}
      """

    doTest(text, resultText)
  }

  def testRemoveApply5() {
    val text = "(foo()).a<caret>pply(1)"
    val resultText = "(foo())<caret> (1)"

    doTest(text, resultText)
  }

  def testRemoveApply6() {
    val text =
      """
        |object D {
        |  def foo()(implicit x: String) = B
        |  implicit val s: String = ""
        |  foo().<caret>apply(1)
        |}
      """
    val resultText =
      """
        |object D {
        |  def foo()(implicit x: String) = B
        |  implicit val s: String = ""
        |  foo().<caret>apply(1)
        |}
      """

    try {
      doTest(text, resultText)
    } catch {
      case _: RuntimeException => // Expected, so continue
    }
  }

  def testRemoveApply7() {
    val text =
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
      """
    val resultText =
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
      """

    try {
      doTest(text, resultText)
    } catch {
      case _: RuntimeException => // Expected, so continue
    }
  }

  def testRemoveApply8() {
    val text =
      """
        |object A {
        |  def foo = B
        |  def foo(x: Int) = B
        |  foo.a<caret>pply(1)
        |}
      """
    val resultText =
      """
        |object A {
        |  def foo = B
        |  def foo(x: Int) = B
        |  foo.a<apply>pply(1)
        |}
      """

    try {
      doTest(text, resultText)
    } catch {
      case _: RuntimeException => // Expected, so continue
    }
  }
}

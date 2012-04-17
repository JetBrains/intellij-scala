package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import codeInsight.intentions.ScalaIntentionTestBase
import codeInsight.intention.expression.RemoveApplyIntention

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
    val text = """
    |  object D {
    |    def foo() = B
    |    foo.ap<caret>ply(1)
    |  }
    """.stripMargin.replace("\r", "").trim
    val resultText = """
    |  object D {
    |    def foo() = B
    |    foo()<caret>(1)
    |  }
    """.stripMargin.replace("\r", "").trim

    doTest(text, resultText)
  }

  def testRemoveApply4() {
    val text = """
    |  object D {
    |    def foo() = B
    |    foo().ap<caret>ply(1)
    |  }
    """.stripMargin.replace("\r", "").trim
    val resultText = """
    |  object D {
    |    def foo() = B
    |    foo()<caret>(1)
    |  }
    """.stripMargin.replace("\r", "").trim

    doTest(text, resultText)
  }

  def testRemoveApply5() {
    val text = "(foo()).a<caret>pply(1)"
    val resultText = "(foo())<caret>(1)"

    doTest(text, resultText)
  }

  //todo HintManager
//  def testRemoveApply6() {
//    val text = """
//    |  object D {
//    |    def foo()(implicit x: String) = B
//    |    implicit val s: String = ""
//    |
//    |    foo().<caret>apply(1)
//    |  }
//    """.stripMargin.replace("\r", "").trim
//    val resultText = """
//    |  object D {
//    |    def foo()(implicit x: String) = B
//    |    implicit val s: String = ""
//    |
//    |    foo().<caret>apply(1)
//    |  }
//    """.stripMargin.replace("\r", "").trim
//
//    doTest(text, resultText)
//  }
}

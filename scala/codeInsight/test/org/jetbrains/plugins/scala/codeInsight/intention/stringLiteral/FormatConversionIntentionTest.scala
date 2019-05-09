package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInsight.intentions

class FormatConversionIntentionTest extends intentions.ScalaIntentionTestBase {
  override def familyName: String = FormatConversionIntention.ConvertToStringConcat

  import EditorTestUtil.{CARET_TAG => CARET}

  def testInterpolatedToConcatenation() {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two"
         |}
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET"one " + x + " two"
         |}
         |"""

    doTest(before, after)
  }

  def testInterpolatedToConcatenation_WithMethodCall() {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two".length
         |}
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET("one " + x + " two").length
         |}
         |"""

    doTest(before, after)
  }

  def testInterpolatedToConcatenation_WithMethodCall2() {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two".substring(23)
         |}
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET("one " + x + " two").substring(23)
         |}
         |"""

    doTest(before, after)
  }

  def testInterpolatedToConcatenation_WithPostfixMethodCall() {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two" length
         |}
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET("one " + x + " two") length
         |}
         |"""

    doTest(before, after)
  }

  def testInterpolatedToConcatenation_WithInfixMethodCall() {
    val before =
      s"""object A {
         |  val x = 42
         |  obj foo ${CARET}s"one $$x two"
         |}
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  obj foo $CARET("one " + x + " two")
         |}
         |"""

    doTest(before, after)
  }
}

package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInsight.intentions

class FormatConversionIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName: String = FormatConversionIntention.ConvertToStringConcat

  def testInterpolatedToConcatenation(): Unit = {
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

  def testInterpolatedToConcatenation_WithMethodCall(): Unit = {
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

  def testInterpolatedToConcatenation_WithMethodCall2(): Unit = {
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

  def testInterpolatedToConcatenation_WithPostfixMethodCall(): Unit = {
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

  def testInterpolatedToConcatenation_WithInfixMethodCall(): Unit = {
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

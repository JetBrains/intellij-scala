package org.jetbrains.plugins.scala.codeInsight.intention.lists

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_3_Latest,
))
abstract class ScalaSplitJoinLineIntentionTestBase extends ScalaIntentionTestBase {
  protected def testType: SplitJoinTestType

  protected def intentionText: String

  /** First argument/parameter of a method/class */
  protected def first: String

  /** Second argument/parameter of a method/class */
  protected def second: String

  /** Arguments/parameters in a single line */
  protected def singleLine: String = s"($first, $second)"

  /** Arguments/parameters in a single line with trailing comma */
  protected def singleLineWithTrailingComma: String = s"($first, $second,)"

  /** Multiline arguments/parameters without new lines after '(' and before ')' */
  protected def noNewLines: String

  /** Multiline arguments/parameters with new line after '(' */
  protected def newLineAfterLeftParen: String

  /** Multiline arguments/parameters with new line before ')' */
  protected def newLineBeforeRightParen: String

  /** Multiline arguments/parameters with new lines after '(' and before ')' */
  protected def newLineAfterLeftParenAndBeforeRightParen: String

  protected def indent(str: String, spaces: Int = 2): String = {
    val baseIndent = " " * spaces
    str.split("\n").map(baseIndent + _).mkString("\n")
  }

  protected def indentLineBreaks(str: String, spaces: Int = 2): String = {
    val baseIndent = " " * spaces
    str.replaceAll("\n", "\n" + baseIndent)
  }

  protected def doTest(singleLineText: String, multiLineText: String): Unit =
    super.doTest(
      text = if (testType.isJoin) multiLineText else singleLineText,
      resultText = if (testType.isJoin) singleLineText else multiLineText,
      expectedIntentionText = Some(intentionText)
    )
}

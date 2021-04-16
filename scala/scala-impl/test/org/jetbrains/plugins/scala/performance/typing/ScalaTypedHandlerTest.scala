package org.jetbrains.plugins.scala.performance.typing

import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps

/**
 * !!! Also see tests in [[org.jetbrains.plugins.scala.lang.actions.editor]] package
 * TODO: unify tests and move to a common package
 */
class ScalaTypedHandlerTest extends TypingTestWithPerformanceTestBase {
  implicit val typingTimeout: Duration = 150 milliseconds

  private val SPACE: Char = ' '

  override protected def folderPath: String = super.folderPath + "/typedHandler/"

  def testCase(): Unit = doFileTest("case _")

  def testDotAfterNewline(): Unit = doFileTest(".")

  def testDotBeforeNewline(): Unit = doFileTest("a")

  def testDefinitionAssignBeforeNewline(): Unit = doFileTest("a")

  def testParametersComaBeforeNewline(): Unit = doFileTest("a")

  def testCompleteScaladocOnSpace(): Unit = {
    doTest(SPACE)(
      s"""class X {
         |  /**$CARET
         |  def foo: Unit
         |}
      """.stripMargin,
      s"""class X {
         |  /** $CARET */
         |  def foo: Unit
         |}
      """.stripMargin
    )
  }

  def testNotCompleteScaladocOnSpaceIfLineIsNotEmpty(): Unit = {
    doTest(SPACE)(
      s"""class X {
         |  /**$CARET some text
         |  def foo: Unit
         |}
         |""".stripMargin,
      s"""class X {
         |  /** $CARET some text
         |  def foo: Unit
         |}
         |""".stripMargin
    )
  }
}

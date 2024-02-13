package org.jetbrains.plugins.scala.performance.typing

import scala.language.postfixOps

/**
 * !!! Also see tests in [[org.jetbrains.plugins.scala.lang.actions.editor]] package
 * TODO: unify tests and move to a common package
 */
class ScalaTypedHandlerTest extends TypingTestWithPerformanceTestBase {
  override protected def folderPath: String = super.folderPath + "/typedHandler/"

  def testCase(): Unit = doFileTest("case _")

  def testDotAfterNewline(): Unit = doFileTest(".")

  def testDotBeforeNewline(): Unit = doFileTest("a")

  def testDefinitionAssignBeforeNewline(): Unit = doFileTest("a")

  def testParametersComaBeforeNewline(): Unit = doFileTest("a")
}

package org.jetbrains.plugins.scala.performance.typing

/**
  * @author Roman.Shein
  *         Date: 15.12.2015
  */
class TypedHandlerTest extends TypingTestWithPerformanceTestBase {

  val typingTimeout = 200

  override protected def folderPath: String = super.folderPath + "/typedHandler/"

  def doTest(stringsToType: String*): Unit = doTest(stringsToType.toList, typingTimeout)

  def testCase() = doTest("case _")

  def testDotAfterNewline() = doTest(".")

  def testDotBeforeNewline() = doTest("a")

  def testDefinitionAssignBeforeNewline() = doTest("a")

  def testParametersComaBeforeNewline() = doTest("a")
}

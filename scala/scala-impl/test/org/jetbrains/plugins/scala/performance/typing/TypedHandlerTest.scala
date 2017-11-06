package org.jetbrains.plugins.scala.performance.typing

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  *         Date: 15.12.2015
  */
@Category(Array(classOf[PerfCycleTests]))
class TypedHandlerTest extends TypingTestWithPerformanceTestBase {

  val typingTimeout = 200

  override protected def folderPath: String = super.folderPath + "/typedHandler/"

  def doTest(stringsToType: String*): Unit = doTest(stringsToType.toList, typingTimeout)

  def testCase(): Unit = doTest("case _")

  def testDotAfterNewline(): Unit = doTest(".")

  def testDotBeforeNewline(): Unit = doTest("a")

  def testDefinitionAssignBeforeNewline(): Unit = doTest("a")

  def testParametersComaBeforeNewline(): Unit = doTest("a")
}

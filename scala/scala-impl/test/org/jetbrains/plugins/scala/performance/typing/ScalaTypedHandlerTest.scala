package org.jetbrains.plugins.scala.performance.typing

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps

@Category(Array(classOf[PerfCycleTests]))
class ScalaTypedHandlerTest extends TypingTestWithPerformanceTestBase {
  implicit val typingTimeout: Duration = 200 milliseconds

  override protected def folderPath: String = super.folderPath + "/typedHandler/"

  def testCase(): Unit = doTest("case _")

  def testDotAfterNewline(): Unit = doTest(".")

  def testDotBeforeNewline(): Unit = doTest("a")

  def testDefinitionAssignBeforeNewline(): Unit = doTest("a")

  def testParametersComaBeforeNewline(): Unit = doTest("a")
}

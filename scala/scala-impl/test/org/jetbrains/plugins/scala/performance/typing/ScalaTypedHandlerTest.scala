package org.jetbrains.plugins.scala.performance.typing

import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.EditorTestUtil.CARET_TAG
import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps

@Category(Array(classOf[PerfCycleTests]))
class ScalaTypedHandlerTest extends TypingTestWithPerformanceTestBase {
  implicit val typingTimeout: Duration = 150 milliseconds

  import EditorTestUtil.{CARET_TAG => CARET}

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

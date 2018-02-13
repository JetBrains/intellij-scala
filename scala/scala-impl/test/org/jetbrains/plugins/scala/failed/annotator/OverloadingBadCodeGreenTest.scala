package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Anton Yalyshev
  */
@Category(Array(classOf[PerfCycleTests]))
class OverloadingBadCodeGreenTest extends BadCodeGreenTestBase {

  override protected def shouldPass: Boolean = false

  import CodeInsightTestFixture.CARET_MARKER

  def testScl2117A(): Unit = {
    val text =
      s"""object Test extends App{
          |  class A
          |  class B extends A
          |  def foo(x: A, y: B) = print(1)
          |  object foo {
          |    def apply(x: B, y: B) = print(3)
          |    def apply(x: A, y: A) = print(5)
          |  }
          |
          |  ${CARET_MARKER}foo(new B, new B)
          |}
      """.stripMargin
    doTest(text)
  }

  def testScl2117B(): Unit = {
    val text =
      s"""object Test {
          |  def apply[T](x1: T) = "one arg"                      // A
          |  def apply[T](x1: T, x2: T) = "two args"              // B
          |  def apply[T](elems: T*) = "var-args: " + elems.size  // C
          |}
          |
          |object Exec {
          |  ${CARET_MARKER}Test(1)
          |}
      """.stripMargin
    doTest(text)
  }
}

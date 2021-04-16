package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Anton Yalyshev
  */
class OverloadingBadCodeGreenTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

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
          |  ${CARET}foo(new B, new B)
          |}
      """.stripMargin
    checkHasErrorAroundCaret(text)
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
          |  ${CARET}Test(1)
          |}
      """.stripMargin
    checkHasErrorAroundCaret(text)
  }
}

package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class ForComprehensionExpectedTypeTest extends ScalaLightCodeInsightFixtureTestAdapter {
  private def doTest(fooBody: String): Unit = {
    val code =
      s"""
        |object Foo {
        |  final class Example[A] {
        |    def map[B](f: A => B): Example[B] = ???
        |    def flatMap[B](f: A => Example[B]): Example[B] = ???
        |    def withFilter(p: A => Boolean): Example[A] = ???
        |  }
        |
        |  val x: Example[A] = ???
        |
        |  class A
        |
        |  class B extends A
        |
        |  def foo: Example[A] = $fooBody
        |}
      """.stripMargin
    
    checkTextHasNoErrors(code)
  }
  
  def testSCL12832_enumerator(): Unit = doTest(
    """
      |for {
      |  a <- x
      |  w = 1
      |} yield new B
    """.stripMargin
  )
  
  def testSCL12832_guard(): Unit = doTest(
    """
      |for {
      |  a <- x if a == null
      |} yield new B
    """.stripMargin
  )
}

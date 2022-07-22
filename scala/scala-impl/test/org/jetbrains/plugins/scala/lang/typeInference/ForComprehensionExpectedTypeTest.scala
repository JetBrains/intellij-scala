package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
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
  
  def testSCL12832_forBinding(): Unit = doTest(
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

  def test_multiple_enumerators(): Unit = doTest(
    """
      |{
      |  for {
      |    a <- x
      |    b = a
      |    c = x
      |    d <- c
      |    if b != null
      |    if c != null
      |    e <- c
      |    f = d
      |  } yield (a, b, c, d, e, f)
      |} map { _._6 }
    """.stripMargin
  )
}

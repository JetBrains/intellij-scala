package org.jetbrains.plugins.scala.annotator

/**
  * @author Alefas
  * @since 15/03/2017
  */
class ScalaHighlightingTest extends ScalaHighlightingTestBase {
  def testSCL4717(): Unit = {
    val scalaText = """
      |object SCL4717 {
      |  def inc(x: Int) = x + 1
      |  def inc2()(x: Int) = x + 1
      |  def foo(f: Int => Unit) = f
      |
      |  val g: Int => Unit = inc _
      |  foo(inc _)
      |  foo(inc)
      |  foo(inc2() _)
      |  foo(inc2())
      |}
    """.stripMargin.trim
    val errors = errorsFromScalaCode(scalaText)
    assert(errors.isEmpty)
  }
}

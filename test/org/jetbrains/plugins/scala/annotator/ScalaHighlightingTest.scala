package org.jetbrains.plugins.scala.annotator

import org.junit.Assert

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

  def testSCL8267(): Unit = {
    val scalaText =
      """
        |object SCL8267 {
        |  val l: Option[List[Int]] = Some(List(1, 2, 3))
        |
        |  (l.map {
        |    for {
        |      x <- _
        |      z = x + 1
        |    } yield x + 1
        |  }, l.map {
        |    for {
        |      x <- List(1, 2, 3)
        |      z <- _
        |    } yield x + z
        |  }, l.map {
        |    1 match {
        |      case _ if _.length == 1 => 123
        |    }
        |  })
        |}
      """.stripMargin
    val errors = errorsFromScalaCode(scalaText)
    assert(errors.isEmpty)
  }

  def testSCL6379(): Unit = {
    val scalaText =
      """
        |class Types {
        |  val zero: Short = 0
        |  val 128 = "a"
        |  val 128 = 128 //it's valid here
        |  val asciiUpperLimit: Short = 128
        |  val pi : Double = 3.141592654
        |  val mathematician :String = "Euler"
        |}
      """.stripMargin
    val errors = errorsFromScalaCode(scalaText)
    assert(errors.length == 1)
    errors.head match {
      case Error(_, message) =>
        Assert.assertEquals(message, "Pattern type is incompatible with expected type, found: Int, required: String")
    }
  }
}

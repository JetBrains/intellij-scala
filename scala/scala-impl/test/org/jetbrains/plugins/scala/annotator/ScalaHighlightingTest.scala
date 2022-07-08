package org.jetbrains.plugins.scala.annotator

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
    assertNothing(errorsFromScalaCode(scalaText))
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
    assertNothing(errorsFromScalaCode(scalaText))
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
    assertMatches(errorsFromScalaCode(scalaText)){
      case Error("128", "Pattern type is incompatible with expected type, found: Int, required: String") :: Nil =>
    }
  }

  def testAbstractPrivateNativeMethod(): Unit = {
    val scalaText =
      """
      |import scala.native
      |
      |class MyClass {
      |  @native private def myNativeMethod: Integer
      |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(scalaText))
  }

  def testSCL14238(): Unit = {
    val code =
      """
        |trait X
        |trait Y extends X
        |
        |trait Bug {
        |  def maybeY(): Option[Y] = ???
        |
        |  def x(): X = ???
        |
        |  maybeY().getOrElse {
        |    x()
        |  }: X
        |
        |  maybeY().getOrElse (
        |    x()
        |  ): X
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL14257(): Unit = {
    val code =
      """
        |class BugReport[N](a: N, b: N)(implicit numeric: Numeric[N]) {
        |
        |  import numeric._
        |
        |  def isASmaller: Boolean = a < b
        |}
        |""".stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

}

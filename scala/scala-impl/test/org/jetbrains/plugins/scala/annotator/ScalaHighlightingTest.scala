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
      case Error(_, "Pattern type is incompatible with expected type, found: Int, required: String") :: Nil =>
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

  def testSCL13532(): Unit = {
    val scalaText =
      """
        |object Test {
        |    def foo[F[_], A](a: F[F[A]]): Any = ???
        |    def either1: Either[String, Either[String, Int]] = ???
        |    foo(either1)
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(scalaText))
  }

  def testSCL12708(): Unit = {
    val code =
      """
        |trait T
        |case object V extends T
        |
        |case class Clz(exprs: T*)
        |
        |def create[P](args: Seq[T], creator: (T*) => Clz) = {
        |  creator(args :_*)
        |}
        |
        |create[Clz](Seq(V, V, V), Clz.apply)
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }
}

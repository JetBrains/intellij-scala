package org.jetbrains.plugins.scala.annotator

class AnnotatorHeavyTest extends ScalaHighlightingTestBase {
  def testScl8684(): Unit =
    assertErrors(
      """
        |import scala.concurrent.Future
        |import scala.concurrent.ExecutionContext.Implicits.global
        |
        |object Test {
        |  def foo() = {
        |    for {
        |      x <- Future(1)
        |      y <- Option(1)
        |    } yield x + y
        |  }
        |}
      """.stripMargin,
      Error("y <- Option(1)", "Expression of type Option[Int] doesn't conform to expected type Future[S_]")
    )

  def testSCL8983(): Unit =
    assertErrors(
      """
        |class Foo extends ((String,String) => String) with Serializable{
        |  override def apply(v1: String, v2: String): String = {
        |    v1+v2
        |  }
        |}
        |
        |object main extends App {
        |  val x = "x"
        |  val y = "y"
        |  val string: Foo = new Foo()(x,y)
        |}
      """.stripMargin,
      Error("new Foo()(x,y)", "Expression of type String doesn't conform to expected type Foo")
    )

  def testSCL10432(): Unit =
    assertErrors(
      s"""
         |sealed abstract class CtorType[-P]
         |case class Hello[-P >: Int <: AnyVal]() extends CtorType[P] {
         |  def hello(p: P) = 123
         |}
         |
         |trait Component[-P, CT[-p] <: CtorType[p]] {
         |  val ctor: CT[P]
         |}
         |
         |implicit def toCtorOps[P >: Int <: AnyVal, CT[-p] <: CtorType[p]](base: Component[P, CT]) =
         |  base.ctor
         |
         |val example: Component[Int, Hello] = ???
         |example.ctor.hello(123)
         |val left: Int = example.hello(123)
      """.stripMargin,
      Error("Hello", "Type constructor Hello does not conform to CT[p]")
    )

  //excluding Some, None from import fix highlighting problems
  //e.g. import scala.{Some => _, None => _, Option => _, Either => _, _}
  def testSCL9818(): Unit =
    assertNoErrors(
      """
        |import scala.{Option => _, Either => _, _}
        |
        |sealed trait Option[+A] {
        |  def map[B](f: A => B): Option[B] = this match {
        |    case None => None
        |    case Some(a) => Some(f(a))
        |  }
        |  def getOrElse[B>:A](default: => B): B = this match {
        |    case None => default
        |    case Some(a) => a
        |  }
        |}
        |case class Some[+A](get: A) extends Option[A]
        |case object None extends Option[Nothing]
        |
        |object BugReport extends App {
        |  println("hello " + Some("w").map(_ + "orld").getOrElse("rong"))
        |}
      """.stripMargin)

  def testSCL10352(): Unit =
    assertNoErrors(
      """
        |class someAnnotation extends scala.annotation.StaticAnnotation
        |
        |class BlockAnnotationExample {
        |  def hello: String = {
        |    {
        |      println("Something")
        |    }: @someAnnotation
        |
        |    "Hello world"
        |  }
        |}
      """.stripMargin)

}

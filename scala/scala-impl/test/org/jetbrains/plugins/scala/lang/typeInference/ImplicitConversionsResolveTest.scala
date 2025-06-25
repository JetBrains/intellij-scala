package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ImplicitConversionsResolveTest extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL17570(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       | val l: Long = 1
       | val l2: java.lang.Long = 1
       |}
       |""".stripMargin
  )

  def testSCL20378(): Unit = checkTextHasNoErrors(
    """
      |import Conversions._
      |
      |object Test {
      |  def main(args: Array[String]): Unit = {
      |    println {
      |      1.convert[String](10.0)
      |    }
      |  }
      |}
      |
      |object Conversions {
      |
      |  implicit class GenericConversion2[A, B](x: A) {
      |    def convert[R](y: B)(implicit f: (A, B) => R): R = f(x, y)
      |  }
      |
      |  implicit val intToStringM: (Int, Double) => String = (x, y) => {
      |      (y + x).toString
      |    }
      |}
      |""".stripMargin
  )

  def testSCL15323(): Unit = checkTextHasNoErrors(
    """
      |object SelfTypeTests {
      |  trait Foo {
      |    def foo(): Int = 42
      |  }
      |
      |  object Foo {
      |    implicit class Ext(private val f: Foo) extends AnyVal {
      |      def fooExt(): Int = 23
      |    }
      |  }
      |
      |  trait Bar { self: Foo =>
      |    def bar(): Int = {
      |      self.foo()
      |      self.fooExt()
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def testSCL22040(): Unit = checkTextHasNoErrors(
    """
      |object Example {
      |
      |  class Foo[A](val v: A)
      |
      |  class FooConverter[A] extends (A => Foo[A]) {
      |    override def apply(a: A): Foo[A] = new Foo[A](a)
      |  }
      |  implicit def toFooConverter[A]: FooConverter[A] = new FooConverter[A]
      |
      |  def fooValue[A](foo: Foo[A]): A = foo.v
      |
      |  private val x: Int = fooValue(1)
      |
      |}
      |""".stripMargin
  )
}


@Category(Array(classOf[TypecheckerTests]))
class ImplicitConversionsScala3ResolveTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_7

  def testSCL21884(): Unit = checkTextHasNoErrors(
    """
      |trait Foo[A]
      |object Foo
      |
      |extension (foo: Foo.type) def derived[A] = ???
      |
      |case class Bar() derives Foo
      |""".stripMargin
  )


  def testSCL23230(): Unit = checkTextHasNoErrors(
    """
      |class Vec(val x: Double, val y: Double)
      |
      |trait Context[V]
      |
      |implicit class VecOps1[V](lhs: V)(using v: Context[V]):
      |  def foo1: Int = 0
      |
      |implicit class VecOps2[V](lhs: V)(implicit v: Context[V]):
      |  def foo2: Int = 0
      |
      |extension [V](lhs: V)(using v: Context[V])
      |  def foo3: Int = 0
      |
      |extension [V](lhs: V)
      |  def foo4(using v: Context[V]): Int = 0
      |
      |object Main:
      |  def main(args: Array[String]): Unit =
      |    val vec = new Vec(0, 1)
      |
      |    given Context[Vec] = ???
      |
      |    vec.foo1
      |    vec.foo2
      |    vec.foo3
      |    vec.foo4
      |""".stripMargin
  )

  def testSimpleLeadingUsingClause(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait X
      |  given X = ???
      |  given X => Conversion[Int, String] = ???
      |  val s: String = 123
      |
      |  trait Foo
      |  implicit def int2Foo(using X)(i: Int): Foo = ???
      |  val f: Foo = 123
      |}
      |""".stripMargin
  )

  def testLeadingUsingDependentSubst(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Foo { type X }
      |  given Foo { type X = Int }
      |  given Int = 123
      |  implicit def int22s(using f: Foo)(i: f.X)(using f.X): String = ???
      |  val x: String = 123
      |}
      |""".stripMargin
  )

  def testLeadingUsingTypeParamSubst(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |trait Bar[A]
      |  given bb: Bar[Int] = ???
      |  given Int = 123
      |  implicit def int2s[A](using Bar[A])(i: A)(using A): String = ???
      |  val s: String = 123
      |}
      |""".stripMargin
  )

  def testInterleavedUsingClause(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait F2
      |  trait B2[A]
      |  given F2 = new F2 {}
      |  given B2[String] = ???
      |  implicit def int2s[A](using F2)(i: Int)(using B2[A]): A = ???
      |  val s: String = 1
      |}
      |""".stripMargin
  )
}

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

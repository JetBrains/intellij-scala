package org.jetbrains.plugins.scala.failed
package annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator._
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/24/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ApplicationAnnotatorTest extends ApplicationAnnotatorTestBase {
  override protected def shouldPass: Boolean = false

  def testSCL4655(): Unit = {
    assertMatches(messages(
      """
        |object SCL4655 {
        |  import scala.collection.JavaConversions.mapAsScalaMap
        |
        |  class MyTestObj
        |  def test(): Unit = {
        |    getMap[MyTestObj]("test") = new MyTestObj //good code red
        |  }
        |
        |  def getMap[T]() = new java.util.HashMap[String, T]
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }

  //path dependent types?
  def testSCL9468A(): Unit = {
    assertMatches(messages(
      """
        |trait Foo {
        |  trait Factory {
        |    type Repr[~]
        |
        |    def apply[S](obj: Repr[S]): Any
        |  }
        |
        |  def apply[S](map: Map[Int, Factory], tid: Int, obj: Any): Any =
        |    map.get(tid).fold(???)(f => f(obj.asInstanceOf[f.Repr[S]]))
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }

  def testSCL9468B(): Unit = {
    assertMatches(messages(
      """
        |class Test {
        |  def c = 1
        |  def c(i: Option[Int]): Int = i.get + 1
        |  def main(args: Array[String]): Unit = {
        |    val a = new A()
        |    a.f(c)
        |  }
        |}
        |
        |class A {
        |  def f[A](m: Option[A] => A) = m(None)
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }

  def testSCL7021(): Unit = {
    assertMatches(messages(
      """trait Base {
        |  def foo(default: Int = 1): Any
        |}
        |
        |object Test {
        |  private val anonClass = new Base() {
        |    def foo(default: Int): Any = ()
        |  }
        |
        |  anonClass.foo()
        |}""".stripMargin
    )) {
      case Nil =>
    }
  }
}

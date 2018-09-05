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

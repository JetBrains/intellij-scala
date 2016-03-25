package org.jetbrains.plugins.scala.failed
package annotator

import org.jetbrains.plugins.scala.annotator._

/**
  * Created by kate on 3/24/16.
  */
class ApplicationAnnotator extends ApplicationAnnotatorTestBase {
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
  def testSCL9468(): Unit = {
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
}

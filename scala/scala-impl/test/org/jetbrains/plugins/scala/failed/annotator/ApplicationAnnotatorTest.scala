package org.jetbrains.plugins.scala.failed
package annotator

import org.jetbrains.plugins.scala.annotator._

/**
  * Created by kate on 3/24/16.
  */
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

  def testSCL13211(): Unit = {
    assertMatches(messages(
      """object Glitch {
        |  object myif {
        |    def apply(cond: Boolean)(block: => Unit): MyIf = {
        |      new MyIf(cond)
        |    }
        |  }
        |  class MyElseIfClause(val cond : Boolean, _block: => Unit){
        |    def unary_! : MyElseIfClause = new MyElseIfClause(!cond, _block)
        |    def block = _block
        |  }
        |
        |  implicit class MyElseIfClauseBuilder(cond : Boolean){
        |    def apply(block : => Unit) : MyElseIfClause = new MyElseIfClause(cond, block)
        |  }
        |
        |  class MyIf (prevCond: Boolean) {
        |    def myelseif (clause : MyElseIfClause) : MyIf = privMyElseIf(clause.cond)(clause.block)
        |    private def privMyElseIf (cond : Boolean)(block: => Unit) : MyIf = {
        |      new MyIf(prevCond || cond)
        |    }
        |    def myelse (block: => Unit) {
        |      val cond = !prevCond
        |    }
        |  }
        |
        |  myif(true) {
        |  } myelseif (!false) { //Cannot resolve symbol !
        |  } myelse {
        |  }
        |}""".stripMargin
    )) {
      case Nil =>
    }
  }
}

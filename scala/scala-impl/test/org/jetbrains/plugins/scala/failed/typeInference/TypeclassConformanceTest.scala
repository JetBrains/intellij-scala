package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

class TypeclassConformanceTest extends TypeConformanceTestBase {

  override protected def shouldPass = false

  def testSCL10850(): Unit = doTest(
    s"""
       |  trait Foo[A] {
       |    type B
       |    def apply(a: A): B
       |  }
       |
       |  implicit val intFoo = new Foo[Int] {
       |    override type B = Double
       |    override def apply(a: Int): B = a.toDouble
       |  }
       |
       |  def fooId[A] = new Foo[A] {
       |    override type B = A
       |    override def apply(a: A): A = a
       |  }
       |
       |  def foo[T](t: T)(implicit f: Foo[T] = fooId[T]): f.B = f(t)
       |
       |  ${caretMarker}val test: String = foo("Some String")
       |//true
       """.stripMargin)
}

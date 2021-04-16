package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * Created by kate on 3/23/16.
  */
class SelfTypeTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL5571(): Unit = doTest()

  def testSCL5947(): Unit = doTest()

  def testSCL10173a(): Unit = doTest(
    s"""
       |trait T
       |class Root
       |class A extends Root with T
       |class B extends A {
       |  def foo(node: Root with T): Unit = {}
       |}
       |
       |object Example extends App {
       |
       |  def bug1(b: B): Unit = {
       |    val a: A = new A()
       |    b.foo(${START}a$END)
       |  }
       |}
       |//b.type
      """.stripMargin)

  def testSCL10173b(): Unit = doTest(
    s"""
       |trait T
       |class Root
       |class A extends Root with T
       |class B extends A {
       |  def foo(node: Root with T): Unit = {}
       |}
       |
       |object Example extends App {
       |  def bug2(b: B): Unit = {
       |    val b2: B = new B()
       |    b.foo(${START}b2$END)
       |  }
       |}
       |//b.type
      """.stripMargin)

  def testSCL8648(): Unit = {
    doTest(
      s"""
        |trait C {
        |    type T
        |  }
        |
        |  trait A[S]
        |
        |  trait B {
        |    def bar(x : A[C { type T }]) : A[C] = ${START}x$END
        |  }
        |//A[C]
      """.stripMargin)
  }

  def testSCL3959(): Unit = {
    doTest(
      s"""
         |class Z[T]
         |class B[T] {
         |  def foo(x: T) = x
         |}
         |
         |def foo1[T]: Z[T] = new Z[T]
         |def goo1[T](x: Z[T]): B[T] = new B[T]
         |goo1(foo1) foo ${START}1$END
         |//Nothing
      """.stripMargin)
  }

  def testSCL7493(): Unit = {
    doTest(
      s"""
         |  class Foo[T,+U] {}
         |  class Bar[T] {}
         |  type FooBar[T,U] = Foo[T,Bar[U]]
         |
         |  def baz[T,U](x: FooBar[T,U], y: FooBar[T,_]): FooBar[T,U] = x
         |
         |  val s1: FooBar[String,Int] = null
         |  val s2: FooBar[String,Boolean] = null
         |  baz[String,Int](s1,${START}s2$END)
         |
         |//FooBar[String,_]
      """.stripMargin)
  }

  def testSCL9471(): Unit = {
    doTest(
      s"""
         |  object Foo {
         |  trait Sys[S <: Sys[S]] {
         |    type I <: Sys[I]
         |  }
         |
         |  def apply[S <: Sys[S]](system: S): Any =
         |    prepare[S, system.I](${START}system$END)
         |
         |  private def prepare[S <: Sys[S], I1 <: Sys[I1]](system: S {type I = I1}): Any = ???
         |}
         |//S{type I = system.I}
      """.stripMargin)
  }
}

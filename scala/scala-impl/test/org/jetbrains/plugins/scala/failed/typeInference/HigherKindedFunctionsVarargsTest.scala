package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author mucianm 
  * @since 07.04.16.
  */
class HigherKindedFunctionsVarargsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL4789(): Unit = {
    doTest(
      s"""
        |def a[T](q: String, args: Any*): Option[T] = null
        |def b[T](f: (String, Any*) => Option[T]) = null
        |b(${START}a$END)
        |//(String, Any*) => Option[Nothing]
      """.stripMargin)
  }

  def testSCL11489(): Unit = {
    doTest(
      s"""
         |  trait AAA {
         |    def whatever(i: Int): Unit = ()
         |  }
         |
         |  trait BBB[+A] extends AAA
         |
         |  object DDD {
         |    def aa(y: AAA): Unit = println("hi there")
         |  }
         |
         |  class CCC[X[_] <: BBB[_]] {
         |    def a[B](x: X[B]): Unit = {
         |      DDD.aa(${START}x$END)
         |      ()
         |    }
         |  }
         |//AAA
      """.stripMargin)
  }

  def testSCL13634(): Unit = {
    doTest(
      s"""
         |trait C[+A, B]
         |  type F[T] = C[Int, T]
         |  def foo(f: F[_]): Unit = ???
         |
         |  val st: C[Int, String] = ???
         |  foo(${START}st$END)
         |//Foo.F[_]
      """.stripMargin)
  }
}

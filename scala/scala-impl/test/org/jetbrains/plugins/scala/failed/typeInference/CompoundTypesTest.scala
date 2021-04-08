package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

/**
  * @author mucianm 
  * @since 04.04.16.
  */
class CompoundTypesTest extends TypeConformanceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL4824_A(): Unit = {
    doTest(
      """
      |trait B
      |trait A extends B
      |class T[T]
      |val a: T[A with B] = new T[B with A]
      |//True
    """.
        stripMargin)
    }

  def testSCL4824_B(): Unit = {
    doTest(
      """
        |trait B
        |trait A extends B { def z = 1 }
        |class T[T]
        |val a: T[A] = new T[B with A {def z: Int}]
        |//True
      """.stripMargin)
  }

  def testSCL13481_1(): Unit = {
    doTest(
      """
        |trait M[A]
        |
        |trait C[A] {
        |  type N <: M[A]
        |  def n: N
        |}
        |
        |def foo[A] =
        |  new C[A] {
        |    type N = M[A]
        |    def n: N = null
        |  }
        |
        |def bar[B] =
        |  new C[B] {
        |    type N = M[B]
        |    /*caret*/val n: N = foo[B].n
        |  }
        |//True""".stripMargin
    )
  }

  def testSCL13481_2(): Unit = {
    doTest(
      """
        |trait M[A]
        |
        |trait C[A] {
        |  type N <: M[A]
        |  def n: N
        |}
        |
        |def foo[A] =
        |  new C[A] {
        |    type N = M[A]
        |    def n: N = null
        |  }
        |
        |val f2: C[String] { type N = M[String] ; def n: M[String] } = foo[String]
        |//True""".stripMargin
    )
  }

  def testSCL13481_3(): Unit = {
    doTest(
      """
        |trait M[A]
        |
        |trait C[A] {
        |  type N <: M[A]
        |  def n: N
        |}
        |
        |def foo[A] =
        |  new C[A] {
        |    type N = M[A]
        |    def n: N = null
        |  }
        |
        |val n1: M[String] = foo[String].n
        |//True""".stripMargin
    )
  }

  def testSCL13481_4(): Unit = {
    doTest(
      """
        |trait M[A]
        |
        |trait C[A] {
        |  type N <: M[A]
        |  def n: N
        |}
        |
        |def foo[A] =
        |  new C[A] {
        |    type N = M[A]
        |    def n: N = null
        |  }
        |
        |val f1: C[String] { def n: M[String] } = foo[String]
        |//True""".stripMargin
    )
  }
}

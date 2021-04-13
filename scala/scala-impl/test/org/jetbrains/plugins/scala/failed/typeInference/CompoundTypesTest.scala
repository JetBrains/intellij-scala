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
}

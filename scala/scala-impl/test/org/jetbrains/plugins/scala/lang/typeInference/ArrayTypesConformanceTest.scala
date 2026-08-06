package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

class ArrayTypesConformanceTest extends TypeConformanceTestBase {
  def testSCL18020(): Unit = doTest(
    """
      |type A2[T] = Array[Array[T]]
      |val a2: A2[Double] = ???
      |val a: Array[Double] = a2
      |//False
      |""".stripMargin
  )
}

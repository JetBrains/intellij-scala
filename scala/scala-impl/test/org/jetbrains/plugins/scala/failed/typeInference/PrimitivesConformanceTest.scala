package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

/**
  * @author mucianm 
  * @since 25.03.16.
  */
class PrimitivesConformanceTest extends TypeConformanceTestBase{

  override protected def shouldPass: Boolean = false

  def testSCL5358(): Unit = doTest(
      """
        |final val x = 0
        |val y: Byte = x
        |/* True */
      """.stripMargin)
}

package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

class StructuralsConformanceTest extends TypeConformanceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL5118(): Unit = {
    doTest(
      """
        |val a: {def toString : String} = "foo"
        |//True
      """.stripMargin)
  }

  def testSCL12611(): Unit = {
    doTest(
      """
        |type Id = Short
        |final val InvalidId: Id = -1
        |//True
      """.stripMargin)
  }
}

package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 25.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class PrimitivesConformanceTest extends TypeConformanceTestBase{
  def test3074() = doTest(
    """
      |val a: Array[Byte] = Array(1, 2, 3)
      |
      |/* True */
    """.stripMargin)

  def testSCL5358() = doTest(
      """
        |final val x = 0
        |val y: Byte = x
        |/* True */
      """.stripMargin)
}

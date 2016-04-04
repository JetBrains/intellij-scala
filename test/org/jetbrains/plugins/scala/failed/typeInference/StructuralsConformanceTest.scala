package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 04.04.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class StructuralsConformanceTest extends TypeConformanceTestBase {

  def testSCL5118(): Unit = {
    doTest(
      """
        |val a: {def toString : String} = "foo"
        |//True
      """.stripMargin)
  }

}

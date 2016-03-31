package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 01.04.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class RecursivePathDependentConformanceTest extends TypeConformanceTestBase {
  def testSCL9858(): Unit = {
    doTest(
      s"""
        |trait TX {
        |  type T
        |}
        |
        |def recursiveFn(a: TX)(b: a.T): a.T = {
        |  ${caretMarker}val res: a.T = recursiveFn(a)(b)   // TX#T does not conform to a.T
        |  res
        |}
        |//true
      """.stripMargin)
  }

}

package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class UnitReturnConformanceTest extends TypeConformanceTestBase {
  def testScl8711(): Unit = {
    val text =
      """def func(i: Int, str: String): Int = i
        |val x: (Int, String) => Unit = func
        |//True""".stripMargin
    doTest(text)
  }
}

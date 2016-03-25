package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class CurriedConformanceTest extends TypeConformanceTestBase {
  def testScl7462(): Unit = {
    doTest(
      """import scala.collection.GenTraversableOnce
        |
        |def f(curry: String)(i: String): Option[String] = Some(i)
        |
        |val x: (String) => GenTraversableOnce[String] = f("curry")
        |//True""".stripMargin)
  }
}

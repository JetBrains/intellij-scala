package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

class NothingConformanceTest extends TypeConformanceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL6634(): Unit = {
    doTest(
      """
        |import scala.collection.mutable
        |
        |val stack: mutable.Stack[String] = mutable.Stack.empty[String]
        |//True
      """.stripMargin)
  }
}

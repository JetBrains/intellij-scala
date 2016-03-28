package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

/**
  * @author Nikolay.Tropin
  */
class SelfTypeProjectionConformanceTest extends TypeConformanceTestBase {
  def testSCL7914(): Unit = {
    val text =
      s"""trait Test {
        |  self: Outer =>
        |
        |  val inner: Inner = new Inner
        |  ${caretMarker}val outerInner: Outer#Inner = inner
        |}
        |
        |class Outer {
        |  class Inner
        |}
        |//true
      """.stripMargin
    doTest(text)
  }
}

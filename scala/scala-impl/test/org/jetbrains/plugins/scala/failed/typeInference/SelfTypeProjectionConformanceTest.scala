package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class SelfTypeProjectionConformanceTest extends TypeConformanceTestBase {

  override protected def shouldPass: Boolean = false

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

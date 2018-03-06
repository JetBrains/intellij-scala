package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 07.04.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class OverloadedErasureExpectedTypeTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {

  override protected def shouldPass: Boolean = false

  import SimpleResolveTestBase._

  def testSCL7222(): Unit ={
    doResolveTest(
      s"""
        |trait Test {
        |  class Foo
        |  class Bar
        |  implicit object I1
        |  implicit object I2
        |  def bar(foo: Foo): Bar
        |  def ${REFTGT}foo(f: Foo => Bar)(implicit i1: I1.type)
        |  def foo(f: Foo => (Foo => Bar))(implicit i2: I2.type)
        |
        |  ${REFSRC}foo(f => bar(f))
        |}
      """.stripMargin)
  }

}

package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author mucianm 
  * @since 05.04.16.
  */
class NamedArgsTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL9144(): Unit = {
    doResolveTest(
      s"""
        |class AB(val a: Int, val b: Int) {
        |  def withAB(x: Int) = ???
        |  def withAB(${REFTGT}a: Int = a, b: Int = b) = ???
        |  def withA(a: Int) = withAB(${REFSRC}a = a)
        |  def withB(b: Int) = withAB(b = b)
        |}
      """.stripMargin)
  }

}

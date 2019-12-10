package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class OverloadedEtaExpansionResolveTest
    extends ScalaLightCodeInsightFixtureTestAdapter
    with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testFunctionMap(): Unit = doResolveTest(
    s"""
       |implicit def f1i(f: (Int, Int, Int) => Int): ((Int, Int)) => Int = ???
       |def f${REFTGT}1(x: Int, y: Int, z: Int): Int = ???
       |def f1(x: Int, y: Int): Int = ???
       |def foo(f: ((Int, Int)) => Int) = f
       |foo(f${REFSRC}1)
       |""".stripMargin
  )
}

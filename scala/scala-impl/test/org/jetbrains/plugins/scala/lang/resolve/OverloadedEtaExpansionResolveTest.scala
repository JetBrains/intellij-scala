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

  def testSCK17085(): Unit = doResolveTest(
    s"""
       |object RedSquibbles extends App {
       |  implicit val x: Int = 0
       |  def redSqui${REFTGT}bble(symbols: Iterable[String])(implicit tdc: Int): Int = 1
       |  def redSquibble(symbol: String): Int = 2
       |  def g(provider: Seq[String] => Int): Unit = {}
       |  g(r${REFSRC}edSquibble)
       |}
       |""".stripMargin
  )
}

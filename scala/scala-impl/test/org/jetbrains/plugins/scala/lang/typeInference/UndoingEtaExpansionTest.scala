package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.LatestScalaVersions.Scala_2_13
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class UndoingEtaExpansionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_13

  def testSimple(): Unit = checkTextHasNoErrors(
    """
      |def foo(f: String => Int): Unit = ()
      |def foo(f: Int => Unit): Int    = 42
      |def bar(x: Int): Unit           = ???
      |
      |foo(x => bar(x))
      |foo(bar _)
      |foo(bar(_))
      |""".stripMargin
  )

  def testWithBlock(): Unit = checkTextHasNoErrors(
    """
      |def foo(f: String => Int): Unit = ()
      |def foo(f: Int => Unit): Int    = 42
      |def bar(x: Int): Unit           = ???
      |
      |foo(x => { bar(x) })
      |""".stripMargin
  )

  def testHigherArity(): Unit = checkTextHasNoErrors(
    """
      |def foo(f: (Int, String) => String): Int       = 123
      |def foo(g: (Double, Double) => Double): Double = 2d
      |def bar(i: Int, s: String): String             = "456"
      |
      |foo(bar(_, _))
      |foo(bar _)
      |foo((x, y) => bar(x, y))
      |foo((x, y) => { bar(x, y) })
      |""".stripMargin
  )
}

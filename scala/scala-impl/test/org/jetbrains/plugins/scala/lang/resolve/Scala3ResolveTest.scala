package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class Scala3ResolveTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testSummon(): Unit = doResolveTest(
    s"""class A {
       |  def ${REFTGT}bar(): Unit = ()
       |}
       |given A = new A
       |
       |def foo(): Unit = {
       |  summon[A].${REFSRC}bar()
       |}
       |""".stripMargin)

}

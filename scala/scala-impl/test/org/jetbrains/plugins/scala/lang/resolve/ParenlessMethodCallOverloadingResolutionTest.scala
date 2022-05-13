package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ParenlessMethodCallOverloadingResolutionTest extends SimpleResolveTestBase {

  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_2_12

  def testSCL16802(): Unit = doResolveTest(
    s"""
       |trait Foo {
       |  def foo(i: Int): String
       |}
       |
       |def ge${REFTGT}tFoo(): Foo = ???
       |def getFoo(s: String): Foo = ???
       |
       |def takesFoo(foo: Foo): Unit = ()
       |takesFoo(getF${REFSRC}oo)
       |
       |""".stripMargin)

}

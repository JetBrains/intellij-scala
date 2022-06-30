package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3ResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testSummon(): Unit = {
    doResolveTest(
      s"""class A {
         |  def ${REFTGT}bar(): Unit = ()
         |}
         |given A = new A
         |
         |def foo(): Unit = {
         |  summon[A].${REFSRC}bar()
         |}
         |""".stripMargin)

    doResolveTest(
      s"def foo: String = Predef.${REFSRC}summon[String]")
  }

  def testStrictEquality(): Unit= doResolveTest(
    s"import scala.language.${REFSRC}strictEquality"
  )

  def testGivenAliasParam(): Unit = doResolveTest(
    s"given mySummon[A](using ${REFTGT}value: A): A = ${REFSRC}value"
  )

  def testTypeVariableMatch(): Unit = doResolveTest(
    s"??? match { case _: Seq[${REFTGT}x] => ??? : ${REFSRC}x }"
  )
}

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

  def testTypeVariableMatchNested(): Unit = doResolveTest(
    s"??? match { case _: Seq[Seq[${REFTGT}x]] => ??? : ${REFSRC}x }"
  )

  def testTypeVariableMatchParentheses(): Unit = doResolveTest(
    s"??? match { case _: (Seq[${REFTGT}x]) => ??? : ${REFSRC}x }"
  )

  def testTypeVariableMatchInfix(): Unit = doResolveTest(
    s"class &&[A, B]; ??? match { case _: (${REFTGT}x && y) => ??? : ${REFSRC}x }"
  )

  def testTypeVariableMatchType(): Unit = doResolveTest(
    s"type T = Seq[Int] match { case Seq[${REFTGT}x] => Option[${REFSRC}x] }"
  )

  def testTypeVariableMatchTypeNested(): Unit = doResolveTest(
    s"type T = Seq[Int] match { case Seq[Seq[${REFTGT}x]] => Option[${REFSRC}x] }"
  )

  def testTypeVariableMatchTypeParentheses(): Unit = doResolveTest(
    s"type T = Seq[Int] match { case (Seq[${REFTGT}x]) => Option[${REFSRC}x] }"
  )

  def testTypeVariableMatchTypeInfix(): Unit = doResolveTest(
    s"class &&[A, B]; type T = Int && Long match { case ${REFTGT}x && y => ${REFSRC}x }"
  )
}

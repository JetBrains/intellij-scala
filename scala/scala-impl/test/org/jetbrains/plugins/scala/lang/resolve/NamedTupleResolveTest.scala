package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class NamedTupleResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_5

  def testTupleExpr(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  val tup = (${REFTGT}x = 1, y = 2)
         |
         |  tup.${REFSRC}x
         |}
         |""".stripMargin
    )

  def testTupleExpr2nd(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  val tup = (x = 1, ${REFTGT}y = 2)
         |
         |  tup.${REFSRC}y
         |}
         |""".stripMargin
    )

  def testTupleType(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  val tup: (${REFTGT}x: Int, y: Int) = ???
         |
         |  tup.${REFSRC}x
         |}
         |""".stripMargin
    )

  def testTupleType2nd(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  val tup: (x: Int, ${REFTGT}y: Int) = ???
         |
         |  tup.${REFSRC}y
         |}
         |""".stripMargin
    )

  def testTupleTypeBehindAlias(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  type Tup = (${REFTGT}x: Int, y: Int)
         |  val tup: Tup = ???
         |
         |  tup.${REFSRC}x
         |}
         |""".stripMargin
    )

  def testTupleExprMerged(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  val tup =
         |    if (???)
         |    then (x = 1, y = 2)
         |    else (${REFTGT}x = 100, y = 200)
         |
         |  tup.${REFSRC}x
         |}
         |""".stripMargin
    )

  def testNameIsAlias(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  type Name = $REFTGT"x"
         |  type Tup = NamedTuple.NamedTuple[(Name, "y"), (Int, Int)]
         |  val tup: Tup = ???
         |
         |  tup.${REFSRC}x
         |}
         |""".stripMargin
    )
}

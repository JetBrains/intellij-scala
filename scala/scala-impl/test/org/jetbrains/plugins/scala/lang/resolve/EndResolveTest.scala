package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase._

class EndResolveTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def test_class(): Unit =
    doResolveTest(
      s"""
         |class T${REFTGT}est
         |end Te${REFSRC}st
         |""".stripMargin
    )

  def test_class_body(): Unit =
    doResolveTest(
      s"""
         |class T${REFTGT}est:
         |  def test = 3
         |end Te${REFSRC}st
         |""".stripMargin
    )

  def test_trait(): Unit =
    doResolveTest(
      s"""
         |trait T${REFTGT}est:
         |  def test = 3
         |end Te${REFSRC}st
         |""".stripMargin
    )

  def test_object(): Unit =
    doResolveTest(
      s"""
         |object T${REFTGT}est:
         |  def test = 3
         |end Te${REFSRC}st
         |""".stripMargin
    )

  def test_enum(): Unit =
    doResolveTest(
      s"""
         |enum T${REFTGT}est:
         |  case X, Y
         |end Te${REFSRC}st
         |""".stripMargin
    )

  def test_given(): Unit =
    doResolveTest(
      s"""
         |${REFTGT}given Any with
         |  def test = 3
         |end ${REFSRC}given
         |""".stripMargin
    )

  def test_new(): Unit =
    doResolveTest(
      s"""
         |val x = ${REFTGT}new:
         |  def test = 3
         |end ${REFSRC}new
         |""".stripMargin
    )

  def test_extension(): Unit =
    doResolveTest(
      s"""
         |${REFTGT}extension (i: Int)
         |  def test = 3
         |end ${REFSRC}extension
         |""".stripMargin
    )

  def test_val(): Unit =
    doResolveTest(
      s"""
         |${REFTGT}val xxx =
         |  def test = 3
         |end ${REFSRC}xxx
         |""".stripMargin
    )

  def test_unnamed_val(): Unit =
    doResolveTest(
      s"""
         |${REFTGT}val _ =
         |  def test = 3
         |end ${REFSRC}val
         |""".stripMargin
    )

  def test_def(): Unit =
    doResolveTest(
      s"""
         |def ${REFTGT}test =
         |  3
         |end ${REFSRC}this
         |""".stripMargin
    )

  def test_this(): Unit =
    doResolveTest(
      s"""
         |class Test:
         |  ${REFTGT}def this(i: Int) =
         |    this()
         |  end ${REFSRC}this
         |""".stripMargin
    )

  def test_catch(): Unit =
    doResolveTest(
      s"""
         |${REFTGT}try 3
         |catch a => 3
         |end ${REFSRC}try
         |""".stripMargin
    )

  def test_finally(): Unit =
    doResolveTest(
      s"""
         |${REFTGT}try 3
         |finally
         |  print()
         |end ${REFSRC}try
         |""".stripMargin
    )
}

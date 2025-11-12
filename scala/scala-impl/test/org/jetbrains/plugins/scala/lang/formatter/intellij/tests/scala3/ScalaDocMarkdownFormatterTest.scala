package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaDocMarkdownFormatterTest extends AbstractScalaFormatterTestBase {
  override protected def version: ScalaVersion = LatestScalaVersions.Scala_3_5


  def test_unordered_list(): Unit = doTextTest(
    """
      |/**
      | * An list:
      | * - Item 1
      | * - Item 2
      | *   Let's do more
      | *   - oho ! sublist
      | *   - another one
      | * - Item 3
      | * Still part of 3
      | */
      |""".stripMargin,
    """
      |/**
      | * An list:
      | * - Item 1
      | * - Item 2
      | *   Let's do more
      | *   - oho ! sublist
      | *   - another one
      | * - Item 3
      | *   Still part of 3
      | */
      |""".stripMargin
  )
}

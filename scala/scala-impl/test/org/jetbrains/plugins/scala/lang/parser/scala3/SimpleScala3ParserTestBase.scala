package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.parser.SimpleScalaParserTestBase

trait SimpleScala3ParserTestBase extends SimpleScalaParserTestBase {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3
}

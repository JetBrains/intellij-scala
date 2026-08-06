package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.lang.parser.SimpleScalaParserTestBase
import org.jetbrains.plugins.scala.project.ScalaFeatures

trait SimpleScala3ParserTestBase extends SimpleScalaParserTestBase {
  override def scalaCodeParsingFeatures: ScalaFeatures = ScalaFeatures.defaultScala3
}

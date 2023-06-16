package org.jetbrains.plugins.scala.lang.parser

import org.jetbrains.plugins.scala.base.{SharedTestProjectToken, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

abstract class SimpleScalaParserTestBase extends SimpleTestCase with ScalaParserTestOps {

  override def parseText(text: String): ScalaFile =
    parseScalaFile(text.withNormalizedSeparator, scalaVersion)

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken(scalaVersion)
}

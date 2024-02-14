package org.jetbrains.plugins.scala.lang.parser

import junit.framework.{Test, TestCase}

class FailedParserTest extends TestCase

object FailedParserTest {
  def suite(): Test = new ScalaFileSetParserTestCase("/parser/failed") {
    override protected def shouldPass = false
  }
}
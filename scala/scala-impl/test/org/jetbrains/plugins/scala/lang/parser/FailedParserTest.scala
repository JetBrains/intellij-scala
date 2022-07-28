package org.jetbrains.plugins.scala
package lang
package parser

import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase

class FailedParserTest extends TestCase

object FailedParserTest {
  def suite(): Test = new ScalaFileSetTestCase("/parser/failed") {
    override protected def shouldPass = false
  }
}
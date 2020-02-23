package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.SimpleTestCase

/**
 * Pavel Fatin
 */

class StringConcatenationParserTest extends SimpleTestCase {
  def testEmpty(): Unit = {
//    assertMatches(parse("")) {
//      case Nil =>
//    }
  }

  // TODO

  private def parse(code: String): Seq[StringPart] = {
    StringConcatenationParser.parse(parseText(code)).get
  }
}

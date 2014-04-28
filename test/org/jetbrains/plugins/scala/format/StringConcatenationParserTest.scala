package org.jetbrains.plugins.scala
package format

import base.SimpleTestCase

/**
 * Pavel Fatin
 */

class StringConcatenationParserTest extends SimpleTestCase {
  def testEmpty() {
//    assertMatches(parse("")) {
//      case Nil =>
//    }
  }

  // TODO

  private def parse(code: String): Seq[StringPart] = {
    StringConcatenationParser.parse(parseText(code)).get
  }
}

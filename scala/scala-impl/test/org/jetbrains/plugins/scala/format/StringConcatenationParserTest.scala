package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SimpleTestCase}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions._
import org.junit.Assert.assertEquals

/**
 * Pavel Fatin
 */

class StringConcatenationParserTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testEmpty(): Unit =
    assertEquals(None, parse(""))

  def testSimple(): Unit =
    assertMatches(parse(""""1" + "2"""").get) {
      case Text("1") :: Text("2") :: Nil =>
    }

  // TODO: more tests

  private def parse(code: String): Option[Seq[StringPart]] = {
    val file = createLightFile(ScalaFileType.INSTANCE, code).asInstanceOf[ScalaFile]
    StringConcatenationParser.parse(file.getFirstChild)
  }
}

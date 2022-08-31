package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.assertEquals

class StringConcatenationParserTest extends ScalaLightCodeInsightFixtureTestCase {

  def testEmpty(): Unit =
    assertEquals(None, parseOpt(""))

  // "string"
  def testFromPlainString(): Unit = doTest(
    """"a b" + "text"""",
    Text("a b") :: Text("text") :: Nil
  )

  def testFromPlainString_WithEscapeChar(): Unit = doTest(
    s""""a \\ a \\\\ a \\\\\\ a" + "text"""",
    Text("a a \\ a \\a") :: Text("text") :: Nil
  )

  def testFromPlainString_WithEscapeUnicode(): Unit = doTest(
    s""""a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b" + "text"""",
    Text("a # \\u0023 \\# \\\\u0023 b") :: Text("text") :: Nil
  )

  // s"string"
  def testFromInterpolatedString_WithEscapeChar(): Unit = doTest(
    s"""s"a \\ a \\\\ a \\\\\\ a" + "text"""",
    Text("a a \\ a \\a") :: Text("text") :: Nil
  )

  def testFromInterpolatedString_WithEscapeUnicode(): Unit = doTest(
    s"""s"a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b" + "text"""",
    Text("a # \\u0023 \\# \\\\u0023 b") :: Text("text") :: Nil
  )

  // raw"string"
  def testFromRawInterpolatedString_WithEscapeChar(): Unit = doTest(
    s"""raw"a \\ a \\\\ a \\\\\\ a" + "text"""",
    Text("a \\ a \\\\ a \\\\\\ a") :: Text("text") :: Nil
  )

  def testFromRawInterpolatedString_WithEscapeUnicode(): Unit = doTest(
    s"""raw"a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b" + "text"""",
    Text("a # \\\\u0023 \\\\# \\\\\\\\u0023 b") :: Text("text") :: Nil
  )

  // """string"""
  // raw"""string"""
  def testFromMultiline(): Unit =
    doTestWithRaw(
      s"""\"\"\"a b\"\"\" + "text"""",
      Text("a b") :: Text("text") :: Nil
    )

  def testFromMultiline_WithEscapeChar(): Unit =
    doTestWithRaw(
      s"""\"\"\"a \\ a \\\\ a \\\\\\ a\"\"\" + "text"""",
      Text("a \\ a \\\\ a \\\\\\ a") :: Text("text") :: Nil
    )

  def testFromMultiline_WithEscapeUnicode(): Unit =
    doTestWithRaw(
      s"""\"\"\"a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b\"\"\" + "text"""",
      Text("a # \\\\u0023 \\\\# \\\\\\\\u0023 b") :: Text("text") :: Nil
    )

  // s"""string"""
  def testFromInterpolatedMultiline(): Unit = doTest(
    s"""s\"\"\"a b\"\"\" + "text"""",
    Text("a b") :: Text("text") :: Nil
  )

  def testFromInterpolatedMultiline_WithEscapeChar(): Unit = doTest(
    s"""s\"\"\"a \\ a \\\\ a \\\\\\ a\"\"\" + "text"""",
    Text("a a \\ a \\a") :: Text("text") :: Nil
  )

  def testFromInterpolatedMultiline_WithEscapeUnicode(): Unit = doTest(
    s"""s\"\"\"a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b\"\"\" + "text"""",
    Text("a # \\u0023 \\# \\\\u0023 b") :: Text("text") :: Nil
  )

  // """string
  //   |string""".stripMargin
  // raw"""string
  //   |string""".stripMargin
  def testFromIMultiline_WithMargins(): Unit =
    doTestWithRaw(
      s"""\"\"\"a b
         |  |a b
         |  |a b
         |  |\"\"\".stripMargin + "text"
         |""".stripMargin,
      Text("a b\na b\na b\n") :: Text("text") :: Nil
    )

  def testFromMultiline_WithMargins_WithEscapeChar(): Unit =
    doTestWithRaw(
      s"""\"\"\"a \\ a \\\\ a \\\\\\ a
         |  |a \\ a \\\\ a \\\\\\ a
         |  |a \\ a \\\\ a \\\\\\ a
         |  |\"\"\".stripMargin + "text"
         |""".stripMargin,
      Text("a \\ a \\\\ a \\\\\\ a\n" +
        "a \\ a \\\\ a \\\\\\ a\n" +
        "a \\ a \\\\ a \\\\\\ a\n") ::
        Text("text") :: Nil
    )

  def testFromMultiline_WithMargins_WithEscapeUnicode(): Unit =
    doTestWithRaw(
      s"""\"\"\"a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b
         |  |a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b
         |  |a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b
         |  |\"\"\".stripMargin + "text"
         |""".stripMargin,
      Text("a # \\\\u0023 \\\\# \\\\\\\\u0023 b\n" +
        "a # \\\\u0023 \\\\# \\\\\\\\u0023 b\n" +
        "a # \\\\u0023 \\\\# \\\\\\\\u0023 b\n") ::
        Text("text") :: Nil
    )

  // s"""string
  //   |string""".stripMargin
  def testFromInterpolatedMultiline_WithMargins(): Unit =
    doTest(
      s"""\"\"\"a b
         |  |a b
         |  |a b
         |  |\"\"\".stripMargin + "text"
         |""".stripMargin,
      Text("a b\na b\na b\n") :: Text("text") :: Nil
    )

  def testFromInterpolatedMultiline_WithMargins_WithEscapeChar(): Unit =
    doTest(
      s"""s\"\"\"a \\ a \\\\ a \\\\\\ a
         |  |a \\ a \\\\ a \\\\\\ a
         |  |a \\ a \\\\ a \\\\\\ a
         |  |\"\"\".stripMargin + "text"
         |""".stripMargin,
      Text("a a \\ a \\a\n" +
        "a a \\ a \\a\n" +
        "a a \\ a \\a\n") ::
        Text("text") :: Nil
    )

  def testFromInterpolatedMultiline_WithMargins_WithEscapeUnicode(): Unit =
    doTest(
      s"""s\"\"\"a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b
         |  |a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b
         |  |a \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 b
         |  |\"\"\".stripMargin + "text"
         |""".stripMargin,
      Text("a # \\u0023 \\# \\\\u0023 b\n" +
        "a # \\u0023 \\# \\\\u0023 b\n" +
        "a # \\u0023 \\# \\\\u0023 b\n") ::
        Text("text") :: Nil
    )

  private def doTest(input: String, expectedParts: Seq[StringPart]): Unit = {
    val actual = parse(input.replace("\r", ""))
    assertEquals(expectedParts, actual)
  }

  private def doTestWithRaw(input: String, expectedParts: Seq[StringPart]): Unit = {
    doTest(input, expectedParts)

    val withRaw = "raw" + input // """text""" -> raw"""text"""
    doTest(withRaw, expectedParts)
  }

  private def parse(code: String): Seq[StringPart] =
    parseOpt(code).get

  private def parseOpt(code: String): Option[Seq[StringPart]] = {
    val file = createLightFile(ScalaFileType.INSTANCE, code).asInstanceOf[ScalaFile]
    StringConcatenationParser.parse(file.getFirstChild)
  }
}

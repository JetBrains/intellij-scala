package org.jetbrains.plugins.scala.codeInsight
package intention
package stringLiteral

import org.jetbrains.plugins.scala.ScalaVersion

abstract class StringToMultilineStringIntentionTest_NoUnicodeEscapesRaw extends StringConversionTestBase {

  override def familyName: String = ScalaCodeInsightBundle.message("family.name.regular.multi.line.string.conversion")

  def testRawInterpolatedSingleLineRoundTripKeepsUnicodeEscapeVerbatim(): Unit = {
    val before =
      s"""object A {
         |  raw"prefix ${CARET}\\u0023 suffix"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  raw'''prefix ${CARET}\\u0023 suffix'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before.replace(CARET, ""))
  }

  def testMultilineSingleLineRoundTripKeepsUnicodeEscapeVerbatim(): Unit = {
    val before =
      s"""object A {
         |  "prefix ${CARET}\\\\u0023 suffix"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  '''prefix ${CARET}\\u0023 suffix'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before.replace(CARET, ""))
  }
}

class StringToMultilineStringIntentionTest_NoUnicodeEscapesRaw_Scala3
  extends StringToMultilineStringIntentionTest_NoUnicodeEscapesRaw {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3
}

class StringToMultilineStringIntentionTest_NoUnicodeEscapesRaw_Scala2_13_XSourceFeatures
  extends StringToMultilineStringIntentionTest_NoUnicodeEscapesRaw {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override protected def additionalCompilerOptions: Seq[String] =
    Seq("-Xsource:3", "-Xsource-features:unicode-escapes-raw")
}

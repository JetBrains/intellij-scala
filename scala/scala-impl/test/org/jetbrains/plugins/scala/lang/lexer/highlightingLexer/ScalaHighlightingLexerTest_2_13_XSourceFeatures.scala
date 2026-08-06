package org.jetbrains.plugins.scala.lang.lexer.highlightingLexer

import org.jetbrains.plugins.scala.ScalaVersion

import java.nio.file.Path

class ScalaHighlightingLexerTest_2_13_XSourceFeatures extends ScalaHighlightingLexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "highlighting_2_13_XSourceFeatures")

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_2_13

  override protected def additionalCompilerOptions: Seq[String] =
    Seq("-Xsource:3", "-Xsource-features:unicode-escapes-raw")
}

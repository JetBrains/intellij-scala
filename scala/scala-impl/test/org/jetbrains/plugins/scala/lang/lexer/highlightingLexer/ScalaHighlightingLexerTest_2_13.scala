package org.jetbrains.plugins.scala.lang.lexer.highlightingLexer

import org.jetbrains.plugins.scala.ScalaVersion

import java.nio.file.Path

class ScalaHighlightingLexerTest_2_13 extends ScalaHighlightingLexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "highlighting_2_13")

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_2_13
}

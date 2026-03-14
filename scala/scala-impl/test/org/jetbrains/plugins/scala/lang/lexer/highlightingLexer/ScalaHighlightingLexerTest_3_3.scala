package org.jetbrains.plugins.scala.lang.lexer.highlightingLexer

import org.jetbrains.plugins.scala.ScalaVersion

import java.nio.file.Path

class ScalaHighlightingLexerTest_3_3 extends ScalaHighlightingLexerTestBase_Scala3 {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "highlighting_3")

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3_3
}

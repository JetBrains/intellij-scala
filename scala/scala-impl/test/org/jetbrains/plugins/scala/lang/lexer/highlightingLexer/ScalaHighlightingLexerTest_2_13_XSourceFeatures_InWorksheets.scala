package org.jetbrains.plugins.scala.lang.lexer.highlightingLexer

import org.jetbrains.plugins.scala.ScalaVersion

import java.nio.file.Path

// This test exists primarily to check that ScalaFeatures for worksheets are detected properly during syntax highlighting
class ScalaHighlightingLexerTest_2_13_XSourceFeatures_InWorksheets extends ScalaHighlightingLexerTest_2_13_XSourceFeatures {

  override protected def scalaFileName = "example.sc"
}

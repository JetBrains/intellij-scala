package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.Scala3Language

import java.nio.file.Path

class Scala3LexerTest extends ScalaLexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "data3")

  override protected def language: Language = Scala3Language.INSTANCE
}

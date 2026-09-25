package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lang.{Language, LanguageParserDefinitions}
import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.junit.Assert.assertEquals

class ShebangLexerBoundaryTest extends LightJavaCodeInsightFixtureTestCase {

  private val languages = Seq(ScalaLanguage.INSTANCE, Scala3Language.INSTANCE)

  private def createLexer(language: Language): Lexer =
    LanguageParserDefinitions.INSTANCE.forLanguage(language).createLexer(getProject)

  def testSCL25035_shebangWithCrLf(): Unit = {
    val shebang = "#!/usr/bin/env -S scala-cli shebang -q"
    val source = s"$shebang\r\nobject Main"

    languages.foreach { language =>
      val lexer = createLexer(language)
      lexer.start(source)

      assertEquals(
        s"${language.getID}: a shebang followed by CRLF must stay a line comment",
        Seq(
          ScalaTokenTypes.tLINE_COMMENT -> shebang,
          ScalaTokenTypes.tWHITE_SPACE_IN_LINE -> "\r\n",
          ScalaTokenType.ObjectKeyword -> "object",
          ScalaTokenTypes.tWHITE_SPACE_IN_LINE -> " ",
          ScalaTokenTypes.tIDENTIFIER -> "Main"
        ),
        tokens(lexer)
      )
    }
  }

  def testSCL25035_restartingAfterTheBeginningDoesNotRecognizeAShebang(): Unit = {
    val source = "// ordinary comment\n#!/usr/bin/env -S scala-cli shebang -q"
    val restartOffset = source.indexOf("#!")

    languages.foreach { language =>
      val lexer = createLexer(language)
      lexer.start(source, restartOffset, source.length, 0)

      assertEquals(
        s"${language.getID}: restarting after file offset zero must not reinterpret #! as a shebang comment",
        ScalaTokenTypes.tIDENTIFIER,
        lexer.getTokenType
      )
      assertEquals(
        s"${language.getID}: restarting after file offset zero must retain the normal Scala token text",
        "#!/",
        lexer.getTokenText
      )
    }
  }

  private def tokens(lexer: Lexer): Seq[(IElementType, String)] = {
    val result = Seq.newBuilder[(IElementType, String)]

    while (lexer.getTokenType != null) {
      result += lexer.getTokenType -> lexer.getTokenText
      lexer.advance()
    }

    result.result()
  }
}

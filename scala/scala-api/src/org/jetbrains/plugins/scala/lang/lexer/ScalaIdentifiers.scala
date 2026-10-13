package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.openapi.util.text.StringUtil.isEmpty
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{KEYWORDS, SOFT_KEYWORDS, tIDENTIFIER}
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

/**
 * Lexer-based checks of Scala names that need neither a project nor PSI.
 *
 * @see [[org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator]]
 * @see [[org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil]]
 */
object ScalaIdentifiers {

  private val keywordNames: Set[String] = KEYWORDS.getTypes
    .iterator
    // Todo: this is a temporary solution for SCL-18347
    //       soft keywords are highly context dependent
    //       and need another way of handling them (tracking issue: SCL-18509)
    .filterNot(SOFT_KEYWORDS.contains)
    .map(_.toString)
    .toSet

  private val lexerCache: UnloadableThreadLocal[ScalaLexer] =
    new UnloadableThreadLocal(new ScalaLexer(false))

  def isIdentifier(name: String): Boolean = {
    if (isEmpty(name)) return false

    val lexer = lexerCache.value
    lexer.start(name)

    if (lexer.getTokenType != tIDENTIFIER) return false
    lexer.advance()
    lexer.getTokenType == null
  }

  def isKeyword(name: String): Boolean =
    keywordNames.contains(name)

  //This method is a copy of:
  //dotty.tools.dotc.util.Chars.isOperatorPart
  //dotty.tools.dotc.util.Chars#isSpecial(int)
  def isOpCharacter(character: Char): Boolean = character match {
    case '~' | '!' | '@' | '#' | '%' | '^' | '*' | '+' | '-' | '<' | '>' | '?' | ':' | '=' | '&' | '|' | '/' | '\\' => true
    case _ =>
      import Character.*
      getType(character) match {
        case MATH_SYMBOL | OTHER_SYMBOL => true
        case _ => false
      }
  }
}

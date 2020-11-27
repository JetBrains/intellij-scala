package scala

import scala.annotation.switch
import java.lang.{Character => JCharacter}

package object quoted {
  // Reproduces the API of https://github.com/lampepfl/dotty/blob/M2/compiler/src/scala/quoted/runtime/impl/printers/SyntaxHighlight.scala
  def highlightKeyword(str: String): String = str
  def highlightTypeDef(str: String): String = str
  def highlightLiteral(str: String): String = str
  def highlightValDef(str: String): String = str
  def highlightOperator(str: String): String = str
  def highlightAnnotation(str: String): String = str
  def highlightString(str: String): String = str
  def highlightTripleQs: String = ""

  // Reproduces the API of https://github.com/lampepfl/dotty/blob/M2/compiler/src/dotty/tools/dotc/util/Chars.scala
  def isOperatorPart(c : Char) : Boolean = (c: @switch) match {
    case '~' | '!' | '@' | '#' | '%' |
         '^' | '*' | '+' | '-' | '<' |
         '>' | '?' | ':' | '=' | '&' |
         '|' | '/' | '\\' => true
    case c => isSpecial(c)
  }
  def isSpecial(c: Char): Boolean = {
    val chtp = JCharacter.getType(c)
    chtp == JCharacter.MATH_SYMBOL.toInt || chtp == JCharacter.OTHER_SYMBOL.toInt
  }
}

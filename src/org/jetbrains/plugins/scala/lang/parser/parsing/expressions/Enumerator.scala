package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.{Guard, Pattern1}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Enumerator ::= Generator
 *              | Guard
 *              | 'val' Pattern1 '=' Expr
 */
object Enumerator extends Enumerator {
  override protected def expr = Expr
  override protected def generator = Generator
  override protected def guard = Guard
  override protected def pattern1 = Pattern1
}

trait Enumerator {
  protected def expr: Expr
  protected def generator: Generator
  protected def guard: Guard
  protected def pattern1: Pattern1

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val enumMarker = builder.mark

    def parseNonGuard(f: Boolean): Boolean = {
      if (!pattern1.parse(builder)) {
        if (!f) {
          builder error ErrMsg("wrong.pattern")
          enumMarker.done(ScalaElementTypes.ENUMERATOR)
          return true
        } else if (!guard.parse(builder, noIf = true)) {
          enumMarker.rollbackTo()
          return false
        } else {
          enumMarker.drop()
          return true
        }
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tASSIGN =>
          builder.advanceLexer() //Ate =
        case ScalaTokenTypes.tCHOOSE =>
          enumMarker.rollbackTo()
          return generator parse builder
        case _ =>
          if (!f) {
            builder error ErrMsg("choose.expected")
            enumMarker.done(ScalaElementTypes.ENUMERATOR)
            return true
          } else {
            enumMarker.rollbackTo()
            return guard.parse(builder, noIf = true)
          }
      }
      if (!expr.parse(builder)) {
        builder error ErrMsg("wrong.expression")
      }
      enumMarker.done(ScalaElementTypes.ENUMERATOR)
      true
    }

    builder.getTokenType match {
      case ScalaTokenTypes.kIF =>
        guard parse builder
        enumMarker.drop()
        true
      case ScalaTokenTypes.kVAL =>
        builder.advanceLexer() //Ate val
        parseNonGuard(false)
      case _ =>
        parseNonGuard(true)
    }
  }
}
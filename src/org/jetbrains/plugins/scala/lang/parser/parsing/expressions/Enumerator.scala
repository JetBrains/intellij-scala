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

object Enumerator {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val enumMarker = builder.mark

    def parseNonGuard(f: Boolean): Boolean = {
      if (!Pattern1.parse(builder)) {
        if (!f) {
          builder error ErrMsg("wrong.pattern")
          enumMarker.done(ScalaElementTypes.ENUMERATOR)
          return true
        } else if (!Guard.parse(builder, true)) {
          enumMarker.rollbackTo()
          return false
        } else {
          enumMarker.drop
          return true
        }
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tASSIGN => {
          builder.advanceLexer //Ate =
        }
        case ScalaTokenTypes.tCHOOSE => {
          enumMarker.rollbackTo
          return Generator parse builder
        }
        case _ => {
          if (!f) {
            builder error ErrMsg("choose.expected")
            enumMarker.done(ScalaElementTypes.ENUMERATOR)
          }
          else {
            enumMarker.rollbackTo
            Guard.parse(builder, true)
          }
          return true
        }
      }
      if (!Expr.parse(builder)) {
        builder error ErrMsg("wrong.expression")
      }
      enumMarker.done(ScalaElementTypes.ENUMERATOR)
      true
    }

    builder.getTokenType match {
      case ScalaTokenTypes.kIF => {
        Guard parse builder
        enumMarker.drop
        return true
      }
      case ScalaTokenTypes.kVAL => {
        builder.advanceLexer //Ate val
        return parseNonGuard(false)
      }
      case _ => {
        return parseNonGuard(true)
      }
    }
  }
}
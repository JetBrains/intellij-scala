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
 * ForBinding ::= Generator
 *              | Guard
 *              | 'val' Pattern1 '=' Expr
 */
object ForBinding {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val forBindingMarker = builder.mark

    def parseNonGuard(f: Boolean): Boolean = {
      if (!Pattern1.parse(builder)) {
        if (!f) {
          builder error ErrMsg("wrong.pattern")
          forBindingMarker.done(ScalaElementType.FOR_BINDING)
          return true
        } else if (!Guard.parse(builder, noIf = true)) {
          forBindingMarker.rollbackTo()
          return false
        } else {
          forBindingMarker.drop()
          return true
        }
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tASSIGN =>
          builder.advanceLexer() //Ate =
        case ScalaTokenTypes.tCHOOSE =>
          forBindingMarker.rollbackTo()
          return Generator parse builder
        case _ =>
          if (!f) {
            builder error ErrMsg("choose.expected")
            forBindingMarker.done(ScalaElementType.FOR_BINDING)
            return true
          } else {
            forBindingMarker.rollbackTo()
            return false
          }
      }
      if (!Expr.parse(builder)) {
        builder error ErrMsg("wrong.expression")
      }
      forBindingMarker.done(ScalaElementType.FOR_BINDING)
      true
    }

    builder.getTokenType match {
      case ScalaTokenTypes.kIF =>
        Guard parse builder
        forBindingMarker.drop()
        true
      case ScalaTokenTypes.kVAL =>
        builder.advanceLexer() //Ate val
        parseNonGuard(false)
      case _ =>
        parseNonGuard(true)
    }
  }
}
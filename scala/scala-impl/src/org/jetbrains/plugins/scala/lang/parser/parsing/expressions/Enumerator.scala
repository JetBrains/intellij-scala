package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.{Guard, Pattern1}

/*
 * Enumerator ::= Generator
 *              | Guard
 *              | 'val' Pattern1 '=' Expr
 */
object Enumerator {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val enumeratorMarker = builder.mark

    def parseNonGuard(f: Boolean): Boolean = {
      if (!Pattern1.parse(builder)) {
        if (!f) {
          builder.error(ErrMsg("wrong.pattern"))
          enumeratorMarker.done(ScalaElementType.FOR_BINDING)
          return true
        } else if (!Guard.parse(builder, noIf = true)) {
          enumeratorMarker.rollbackTo()
          return false
        } else {
          enumeratorMarker.drop()
          return true
        }
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tASSIGN =>
          builder.advanceLexer() //Ate =
        case ScalaTokenTypes.tCHOOSE =>
          enumeratorMarker.rollbackTo()
          return Generator.parse(builder)
        case _ =>
          if (!f) {
            builder.error(ErrMsg("choose.expected"))
            enumeratorMarker.done(ScalaElementType.FOR_BINDING)
            return true
          } else {
            enumeratorMarker.rollbackTo()
            return false
          }
      }
      if (!Expr.parse(builder)) {
        builder.error(ErrMsg("wrong.expression"))
      }
      enumeratorMarker.done(ScalaElementType.FOR_BINDING)
      true
    }

    builder.getTokenType match {
      case ScalaTokenTypes.kIF =>
        Guard.parse(builder)
        enumeratorMarker.drop()
        true
      case ScalaTokenTypes.kVAL =>
        builder.advanceLexer() //Ate val
        parseNonGuard(false)
      case _ =>
        parseNonGuard(true)
    }
  }
}
package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.{Guard, Pattern1}

/*
 * Enumerator ::= Generator
 *              | Guard
 *              | 'val' Pattern1 '=' Expr
 */
object Enumerator extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val enumeratorMarker = builder.mark()

    def parseAfterPattern(hasPrefix: Boolean): Boolean = {
      builder.getTokenType match {
        case ScalaTokenTypes.tASSIGN =>
          builder.advanceLexer() //Ate =
          if (!ExprInIndentationRegion()) {
            builder.wrongExpressionError()
          }
          enumeratorMarker.done(ScalaElementType.FOR_BINDING)
          true
        case ScalaTokenTypes.tCHOOSE =>
          enumeratorMarker.rollbackTo()
          Generator()
        case _                       =>
          if (hasPrefix) {
            builder.error(ErrMsg("choose.expected"))
            enumeratorMarker.done(ScalaElementType.FOR_BINDING)
            true
          } else {
            enumeratorMarker.rollbackTo()
            false
          }
      }
    }

    def parseGeneratorOrBinding(hasPrefix: Boolean): Boolean =
      if (Pattern1()) {
        parseAfterPattern(hasPrefix)
      } else if (hasPrefix) {
        builder.error(ErrMsg("wrong.pattern"))
        enumeratorMarker.done(ScalaElementType.FOR_BINDING)
        true
      } else if (!Guard(noIf = true)) {
        enumeratorMarker.rollbackTo()
        false
      } else {
        enumeratorMarker.drop()
        true
      }

    builder.getTokenType match {
      case ScalaTokenTypes.kIF =>
        Guard()
        enumeratorMarker.drop()
        true
      case ScalaTokenTypes.kVAL | ScalaTokenTypes.kCASE=>
        builder.advanceLexer() //Ate val
        // we parse incorrect code like: 1)  for-binding with `case` keyword 2) generator with `val` keyword
        // error highlighting is done in annotator
        parseGeneratorOrBinding(true)
      case _ =>
        parseGeneratorOrBinding(false)
    }
  }
}
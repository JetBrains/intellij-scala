package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils._

/**
 * @author Alexander Podkhalyuzin
 *         Date: 15.02.2008
 */

/*
 *  typeArgs ::= '[' Types ']'
 */

object TypeArgs {
  def parse(builder: ScalaPsiBuilder, isPattern: Boolean): Boolean = build(ScalaElementTypes.TYPE_ARGS, builder) {
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET =>
        builder.advanceLexer() //Ate [
        builder.disableNewlines
        def checkTypeVariable: Boolean = {
          if (isPattern) {
            builder.getTokenType match {
              case ScalaTokenTypes.tIDENTIFIER =>
                val idText = builder.getTokenText
                val firstChar = idText.charAt(0)
                if (firstChar != '`' && firstChar.isLower) {
                  val typeParameterMarker = builder.mark()
                  builder.advanceLexer()
                  typeParameterMarker.done(ScalaElementTypes.TYPE_VARIABLE)
                  true
                } else false
              case _ => false
            }
          } else false
        }

        if (checkTypeVariable || Type.parse(builder)) {
          var parsedType = true
          while (builder.getTokenType == ScalaTokenTypes.tCOMMA && parsedType) {
            builder.advanceLexer()
            parsedType = Type.parse(builder)
            if (!parsedType) builder error ScalaBundle.message("wrong.type")
          }
        } else builder error ScalaBundle.message("wrong.type")

        builder.getTokenType match {
          case ScalaTokenTypes.tRSQBRACKET =>
            builder.advanceLexer() //Ate ]
          case _ => builder error ScalaBundle.message("rsqbracket.expected")
        }
        builder.restoreNewlinesState
        true
      case _ => false
    }
  }
}
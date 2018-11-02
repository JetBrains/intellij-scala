package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * @author Alexander Podkhalyuzin
 *         Date: 15.02.2008
 */

/*
 *  typeArgs ::= '[' Types ']'
 */
object TypeArgs extends TypeArgs {
  override protected def parseComponent(builder: ScalaPsiBuilder): Boolean = Type.parse(builder)
}

trait TypeArgs {
  def parse(builder: ScalaPsiBuilder, isPattern: Boolean): Boolean =
    builder.build(ScalaElementType.TYPE_ARGS) { builder =>
      builder.getTokenType match {
        case ScalaTokenTypes.tLSQBRACKET =>
          builder.advanceLexer() //Ate [
          builder.disableNewlines()

          def checkTypeVariable: Boolean = {
            if (isPattern) {
              builder.getTokenType match {
                case ScalaTokenTypes.tIDENTIFIER =>
                  val idText = builder.getTokenText
                  val firstChar = idText.charAt(0)
                  if (firstChar != '`' && firstChar.isLower) {
                    val typeParameterMarker = builder.mark()
                    val idMarker = builder.mark()
                    builder.advanceLexer()
                    builder.getTokenType match {
                      case ScalaTokenTypes.tCOMMA | ScalaTokenTypes.tRSQBRACKET =>
                        idMarker.drop()
                        typeParameterMarker.done(ScalaElementType.TYPE_VARIABLE)
                        true
                      case _ =>
                        idMarker.rollbackTo()
                        typeParameterMarker.drop()
                        false
                    }
                  } else false
                case _ => false
              }
            } else false
          }

          if (checkTypeVariable || parseComponent(builder)) {
            var parsedType = true
            while (builder.getTokenType == ScalaTokenTypes.tCOMMA && parsedType &&
              !builder.consumeTrailingComma(ScalaTokenTypes.tRSQBRACKET)) {
              builder.advanceLexer()
              parsedType = checkTypeVariable || parseComponent(builder)
              if (!parsedType) builder error ScalaBundle.message("wrong.type")
            }
          } else builder error ScalaBundle.message("wrong.type")

          builder.getTokenType match {
            case ScalaTokenTypes.tRSQBRACKET =>
              builder.advanceLexer() //Ate ]
            case _ => builder error ScalaBundle.message("rsqbracket.expected")
          }
          builder.restoreNewlinesState()
          true
        case _ => false
      }
    }

  protected def parseComponent(builder: ScalaPsiBuilder): Boolean
}
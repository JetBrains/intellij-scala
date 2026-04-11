package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 *  typeArgs ::= '[' Types ']'
 */
object TypeArgs extends TypeArgs {
  override protected def parseComponent(isPattern: Boolean)(implicit builder: ScalaPsiBuilder): Boolean =
    Type(isPattern = isPattern, typeVariables = true)
}

trait TypeArgs {
  def apply(isPattern: Boolean)(implicit builder: ScalaPsiBuilder): Boolean =
    builder.build(ScalaElementType.TYPE_ARGS) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLSQBRACKET =>
          builder.advanceLexer() //Ate [
          builder.disableNewlines()

          def checkTypeVariable: Boolean = {
            if (isPattern) {
              builder.getTokenType match {
                case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
                  val idText = builder.getTokenText
                  val firstChar = idText.charAt(0)
                  if (firstChar == '_' || (firstChar != '`' && firstChar.isLower)) {
                    val typeParameterMarker = builder.mark()
                    val idMarker            = builder.mark()
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

          def parseTypeArg(): Boolean =
            checkTypeVariable || parseComponent(isPattern)

          def parsePositionalTypeArg(): Boolean = {
            val typeArgMarker = builder.mark()
            val parsedTypeArg = parseTypeArg()
            if (parsedTypeArg) typeArgMarker.done(ScalaElementType.TYPE_ARG)
            else typeArgMarker.drop()
            parsedTypeArg
          }

          val mixedTypeArgsError = ScalaBundle.message("named.and.positional.type.arguments.cannot.be.mixed")

          def parseNamedTypeArg(): Boolean =
            if (builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tASSIGN)) {
              val typeArgMarker = builder.mark()
              val namedRefMarker = builder.mark()
              builder.advanceLexer() // Ate id
              namedRefMarker.done(ScalaElementType.REFERENCE)
              builder.advanceLexer() // Ate =
              if (!parseTypeArg()) builder error ScalaBundle.message("wrong.type")
              typeArgMarker.done(ScalaElementType.TYPE_ARG)
              true
            } else {
              val mixedArgMarker = builder.mark()
              val parsedType = parsePositionalTypeArg()
              if (parsedType) {
                // Named and positional type arguments cannot be mixed.
                mixedArgMarker.error(mixedTypeArgsError)
              } else {
                mixedArgMarker.drop()
                val token = builder.getTokenType
                builder error ErrMsg("identifier.expected")
                if (token == ScalaTokenTypes.tASSIGN) {
                  builder.advanceLexer() // Ate =
                }
              }
              parsedType
            }

          val parseNamedArgs = builder.isScala3 && !isPattern && builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tASSIGN)

          var parsedType =
            if (parseNamedArgs) parseNamedTypeArg()
            else parsePositionalTypeArg()

          if (!parsedType) builder error ScalaBundle.message("wrong.type")

          while (builder.getTokenType == ScalaTokenTypes.tCOMMA && parsedType &&
            !builder.consumeTrailingComma(ScalaTokenTypes.tRSQBRACKET)) {
            builder.advanceLexer()
            parsedType =
              if (parseNamedArgs) parseNamedTypeArg()
              else if (builder.isScala3 && !isPattern && builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tASSIGN)) {
                // In positional mode we still consume the named argument for better recovery.
                val mixedArgMarker = builder.mark()
                val parsedNamedTypeArg = parseNamedTypeArg()
                if (parsedNamedTypeArg) mixedArgMarker.error(mixedTypeArgsError)
                else mixedArgMarker.drop()
                parsedNamedTypeArg
              } else parsePositionalTypeArg()

            if (!parsedType) builder error ScalaBundle.message("wrong.type")
          }

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

  protected def parseComponent(isPattern: Boolean)(implicit builder: ScalaPsiBuilder): Boolean
}

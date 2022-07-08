package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Block
import org.jetbrains.plugins.scala.lang.parser.parsing.top.QualId
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Type, TypeArgs}

object MacroDef extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF => builder.advanceLexer()
      case _ =>
        marker.drop()
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        FunSig()
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON =>
            builder.advanceLexer() //Ate :
            if (Type()) {
              builder.getTokenType match {
                case ScalaTokenTypes.tASSIGN =>
                  builder.advanceLexer() //Ate =
                  builder.getTokenType match {
                    case ScalaTokenTypes.kMACRO =>
                      builder.advanceLexer() //Ate `macro`
                      builder.getTokenType match {
                        case ScalaTokenTypes.tLBRACE => // scalameta style - embedded macro body
                          if (builder.twoNewlinesBeforeCurrentToken) {
                            return false
                          }
                          Block.Braced()
                          marker.drop()
                          true
                        case _ =>
                          if (QualId()) {
                            if (builder.getTokenType == ScalaTokenTypes.tLSQBRACKET) {
                              TypeArgs(isPattern = false)
                            }
                            marker.drop()
                            true
                          } else {
                            marker.drop()
                            false
                          }
                      }
                    case _ =>
                      marker.rollbackTo()
                      false
                  }
                case _ =>
                  marker.rollbackTo()
                  false
              }
            }
            else {
              marker.rollbackTo()
              false
            }
          case ScalaTokenTypes.tASSIGN =>
            builder.advanceLexer() //Ate =
            builder.getTokenType match {
              case ScalaTokenTypes.kMACRO =>
                builder.advanceLexer() //Ate `macro`
                builder.getTokenType match {
                  case ScalaTokenTypes.tLBRACE =>  // scalameta style - embedded macro body
                    if (builder.twoNewlinesBeforeCurrentToken) {
                      return false
                    }
                    Block.Braced()
                    marker.drop()
                    true
                  case _ =>
                    if (QualId()) {
                      if (builder.getTokenType == ScalaTokenTypes.tLSQBRACKET) {
                        TypeArgs(isPattern = false)
                      }
                      marker.drop()
                      true
                    } else {
                      marker.drop()
                      false
                    }
                }
              case _ =>
                marker.rollbackTo()
                false
            }
          case _ =>
            marker.rollbackTo()
            false
        }
      case _ =>
        marker.rollbackTo()
        false
    }
  }
}

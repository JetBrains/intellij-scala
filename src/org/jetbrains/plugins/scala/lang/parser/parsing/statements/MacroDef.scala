package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import lexer.ScalaTokenTypes
import builder.ScalaPsiBuilder
import top.Qual_Id
import types.{TypeArgs, Type}

/**
 * @author Jason Zaugg
 *
 * MacroDef ::= MacroDef ::= FunSig [‘:’ Type] ‘=’ ‘macro’ QualId [TypeArgs]
 */
object MacroDef {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark;
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF => builder.advanceLexer()
      case _ => {
        marker.drop()
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        FunSig parse builder
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer() //Ate :
            if (Type.parse(builder)) {
              builder.getTokenType match {
                case ScalaTokenTypes.tASSIGN => {
                  builder.advanceLexer() //Ate =
                  builder.getTokenType match {
                    case ScalaTokenTypes.kMACRO =>
                      builder.advanceLexer() //Ate `macro`
                      if (Qual_Id.parse(builder)) {
                        if (builder.getTokenType == ScalaTokenTypes.tLSQBRACKET) {
                          TypeArgs.parse(builder)
                        }
                        marker.drop()
                        true
                      } else {
                        marker.drop()
                        false
                      }
                    case _ =>
                      marker.rollbackTo()
                      false
                  }
                }
                case _ => {
                  marker.rollbackTo()
                  false
                }
              }
            }
            else {
              marker.rollbackTo()
              false
            }
          }
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer() //Ate =
            builder.getTokenType match {
              case ScalaTokenTypes.kMACRO =>
                builder.advanceLexer() //Ate `macro`
                if (Qual_Id.parse(builder)) {
                  if (builder.getTokenType == ScalaTokenTypes.tLSQBRACKET) {
                    TypeArgs.parse(builder)
                  }
                  marker.drop()
                  true
                } else {
                  marker.drop()
                  false
                }
              case _ =>
                marker.rollbackTo()
                false
            }
          }
          case _ => {
            marker.rollbackTo()
            false
          }
        }
      }
      case _ => {
        marker.rollbackTo()
        false
      }
    }
  }
}

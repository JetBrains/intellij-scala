package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import com.intellij.lang.PsiBuilder
import expressions.{Expr, Block}
import lexer.ScalaTokenTypes
import nl.LineTerminator
import params.ParamClauses
import types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 13.02.2008
*/

/*
 * FunDef ::= FunSig [':' Type] '=' Expr
 *          | FunSig [nl] '{' Block '}'
 *          | 'this' ParamClause ParamClauses
 *            ('=' ConstrExpr | [nl] ConstrBlock)
 */


object FunDef {
  def parse(builder: PsiBuilder): Boolean = {
    val faultMarker = builder.mark;
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF => builder.advanceLexer
      case _ => {
        faultMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        FunSig parse builder
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate :
            if (Type.parse(builder)) {
              builder.getTokenType match {
                case ScalaTokenTypes.tASSIGN => {
                  builder.advanceLexer //Ate =
                  if (Expr.parse(builder)) {
                    faultMarker.drop
                    return true
                  }
                  else {
                    builder error ScalaBundle.message("wrong.expression")
                    faultMarker.drop
                    return true
                  }
                }
                case _ => {
                  faultMarker.rollbackTo
                  return false
                }
              }
            }
            else {
              faultMarker.rollbackTo
              return false
            }
          }
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer //Ate =
            if (Expr.parse(builder)) {
              faultMarker.drop
              return true
            }
            else {
              builder error ScalaBundle.message("wrong.expression")
              faultMarker.drop
              return true
            }
          }
          case ScalaTokenTypes.tLBRACE => {
            Block.parse(builder,true)
            faultMarker.drop
            return true
          }
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (!LineTerminator(builder.getTokenText)) {
              faultMarker.rollbackTo
              return false
            }
            else {
              builder.advanceLexer //Ate nl
              builder.getTokenType match {
                case ScalaTokenTypes.tLBRACE => {
                  Block.parse(builder,true)
                  faultMarker.drop
                  return true
                }
                case _ => {
                  faultMarker.rollbackTo
                  return false
                }
              }
            }
          }
          case _ => {
            faultMarker.rollbackTo
            return false
          }
        }
      }
      case ScalaTokenTypes.kTHIS => {
        builder.advanceLexer //Ate this
        ParamClauses parse (builder, true)
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer //Ate =
            if (!ConstrExpr.parse(builder)) {
              builder error ScalaBundle.message("wrong.constr.expression")
            }
            faultMarker.drop
            return true
          }
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (LineTerminator(builder.getTokenText)) {
              builder.advanceLexer //Ate nl
            }
            else {
              builder error ScalaBundle.message("constr.block.expected")
              faultMarker.drop
              return true
            }
            if (!ConstrBlock.parse(builder)) {
              builder error ScalaBundle.message("constr.block.expected")
            }
            faultMarker.drop
            return true
          }
          case _ => {
            if (!ConstrBlock.parse(builder)) {
              builder error ScalaBundle.message("constr.block.expected")
            }
            faultMarker.drop
            return true
          }
        }
      }
      case _ => {
        faultMarker.rollbackTo
        return false
      }
    }
  }
}
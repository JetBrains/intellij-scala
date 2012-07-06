package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import expressions.{Expr, Block}
import lexer.ScalaTokenTypes
import params.ParamClauses
import types.Type
import builder.ScalaPsiBuilder
import parser.util.ParserPatcher

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
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val faultMarker = builder.mark;
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF => builder.advanceLexer()
      case _ => {
        faultMarker.drop()
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
                  if (Expr.parse(builder)) {
                    faultMarker.drop()
                    true
                  }
                  else {
                    builder error ScalaBundle.message("wrong.expression")
                    faultMarker.drop()
                    true
                  }
                }
                case _ => {
                  faultMarker.rollbackTo()
                  false
                }
              }
            }
            else {
              faultMarker.rollbackTo()
              false
            }
          }
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer() //Ate =
            if (Expr.parse(builder) || ParserPatcher.getSuitablePatcher(builder).parse(builder)) {
              faultMarker.drop()
              true
            }
            else {
              builder error ScalaBundle.message("wrong.expression")
              faultMarker.drop()
              true
            }
          }
          case ScalaTokenTypes.tLBRACE => {
            if (builder.twoNewlinesBeforeCurrentToken) {
              faultMarker.rollbackTo()
              return false
            }
            Block.parse(builder,true)
            faultMarker.drop()
            true
          }
          case _ => {
            faultMarker.rollbackTo()
            false
          }
        }
      }
      case ScalaTokenTypes.kTHIS => {
        builder.advanceLexer() //Ate this
        ParamClauses parse (builder, true)
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer() //Ate =
            if (!ConstrExpr.parse(builder)) {
              builder error ScalaBundle.message("wrong.constr.expression")
            }
            faultMarker.drop()
            true
          }
          case _ => {
            if (builder.twoNewlinesBeforeCurrentToken) {
              builder error ScalaBundle.message("constr.block.expected")
              faultMarker.drop()
              return true
            }
            if (!ConstrBlock.parse(builder)) {
              builder error ScalaBundle.message("constr.block.expected")
            }
            faultMarker.drop()
            true
          }
        }
      }
      case _ => {
        faultMarker.rollbackTo()
        false
      }
    }
  }
}
package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Block, ExprInIndentationRegion}
import org.jetbrains.plugins.scala.lang.parser.parsing.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

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
    val faultMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF => builder.advanceLexer()
      case _ =>
        faultMarker.drop()
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        FunSig parse builder
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON =>
            builder.advanceLexer() //Ate :
            if (Type.parse(builder)) {
              builder.getTokenType match {
                case ScalaTokenTypes.tASSIGN =>
                  builder.advanceLexer() //Ate =
                  if (ExprInIndentationRegion.parse(builder)) {
                    faultMarker.drop()
                    true
                  }
                  else {
                    builder error ScalaBundle.message("wrong.expression")
                    faultMarker.drop()
                    true
                  }
                case _ =>
                  faultMarker.rollbackTo()
                  false
              }
            }
            else {
              faultMarker.rollbackTo()
              false
            }
          case ScalaTokenTypes.tASSIGN =>
            builder.advanceLexer() //Ate =
            builder.skipExternalToken()

            if (ExprInIndentationRegion.parse(builder)) {
              faultMarker.drop()
              true
            }
            else {
              builder error ScalaBundle.message("wrong.expression")
              faultMarker.drop()
              true
            }
          case ScalaTokenTypes.tLBRACE =>
            if (builder.twoNewlinesBeforeCurrentToken) {
              faultMarker.rollbackTo()
              return false
            }
            Block.parse(builder, hasBrace = true)
            faultMarker.drop()
            true
          case _ =>
            faultMarker.rollbackTo()
            false
        }
      case ScalaTokenTypes.kTHIS =>
        builder.advanceLexer() //Ate this
        ParamClauses parse(builder, true)
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN =>
            builder.advanceLexer() //Ate =
            if (!ConstrExpr.parse(builder)) {
              builder error ScalaBundle.message("wrong.constr.expression")
            }
            faultMarker.drop()
            true
          case _ =>
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
      case _ =>
        faultMarker.rollbackTo()
        false
    }
  }
}
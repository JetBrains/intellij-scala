package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Block, ConstrExprInIndentationRegion, ExprInIndentationRegion}
import org.jetbrains.plugins.scala.lang.parser.parsing.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/*
 * FunDef ::= FunSig [':' Type] '=' Expr
 *          | FunSig [nl] '{' Block '}'
 *          | 'this' ParamClause ParamClauses
 *            ('=' ConstrExpr | [nl] ConstrBlock)
 */
object FunDef extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val faultMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF => builder.advanceLexer()
      case _ =>
        faultMarker.drop()
        return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        FunSig()
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON =>
            builder.advanceLexer() //Ate :
            if (!Type()) {
              builder.error(ScalaBundle.message("wrong.type"))
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tASSIGN =>
                builder.advanceLexer() //Ate =
                if (ExprInIndentationRegion()) {
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
          case ScalaTokenTypes.tASSIGN =>
            builder.advanceLexer() //Ate =
            builder.skipExternalToken()

            if (ExprInIndentationRegion()) {
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
            Block.Braced()
            faultMarker.drop()
            true
          case _ =>
            faultMarker.rollbackTo()
            false
        }
      case ScalaTokenTypes.kTHIS =>
        builder.advanceLexer() //Ate this
        ParamClauses(expectAtLeastOneClause = true)

        // just parse a type annotation here, even though it is not correct
        if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
          val wrongTypeMarker = builder.mark()
          builder.advanceLexer() // Ate :
          Type()
          wrongTypeMarker error ScalaBundle.message("auxiliary.constructor.may.not.have.a.type.annotation")
        }

        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN =>
            builder.advanceLexer() //Ate =
            if (!ConstrExprInIndentationRegion()) {
              builder error ScalaBundle.message("wrong.constr.expression")
            }
            faultMarker.drop()
            true
          case _ =>
            if (builder.twoNewlinesBeforeCurrentToken || !ConstrBlock()) {
              builder error ScalaBundle.message("auxiliary.constructor.definition.expected")
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
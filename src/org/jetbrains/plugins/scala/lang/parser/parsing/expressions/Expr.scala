package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * @author Alexander Podkhalyuzin
 * Date: 03.03.2008
 */

/*
 * Expr ::= (Bindings | [‘implicit’] id | ‘_’) ‘=>’ Expr
 *         | Expr1
 *
 * implicit closures are actually implemented in other parts of the parser, not here! The grammar
 * from the Scala Reference does not match the implementation in Parsers.scala.
 */
object Expr extends Expr {
  override protected val expr1 = Expr1
  override protected val bindings = Bindings
}

trait Expr {
  protected val expr1: Expr1
  protected val bindings: Bindings

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val exprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        val pmarker = builder.mark
        builder.advanceLexer() //Ate id
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE =>
            val psm = pmarker.precede // 'parameter clause'
            val pssm = psm.precede // 'parameter list'
            pmarker.done(ScalaElementTypes.PARAM)
            psm.done(ScalaElementTypes.PARAM_CLAUSE)
            pssm.done(ScalaElementTypes.PARAM_CLAUSES)

            builder.advanceLexer() //Ate =>
            if (!parse(builder)) builder error ErrMsg("wrong.expression")
            exprMarker.done(ScalaElementTypes.FUNCTION_EXPR)
            return true
          case _ =>
            pmarker.drop()
            exprMarker.rollbackTo()
        }

      case ScalaTokenTypes.tLPARENTHESIS =>
        if (bindings.parse(builder)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE =>
              builder.advanceLexer() //Ate =>
              if (!parse(builder)) builder error ErrMsg("wrong.expression")
              exprMarker.done(ScalaElementTypes.FUNCTION_EXPR)
              return true
            case _ => exprMarker.rollbackTo()
          }
        }
        else {
          exprMarker.drop()
        }
      case _ => exprMarker.drop()
    }
    expr1.parse(builder)
  }
}
package org.jetbrains.plugins.scala.lang.parser.parsing.expressions{
/**
* @author Ilya Sergey
* Auxiliary expression non-terminals
*/
import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import com.intellij.psi.tree.IElementType;


/*
Argument expressions
Default grammar:

ArgumentExprs ::= ‘(’ [Exprs] ’)’
              | BlockExpr
*/
  object ArgumentExprs {

    def parse(builder : PsiBuilder) : ScalaElementType = {
        val argsMarker = builder.mark()

        // Process ")" symbol
        def closeParent: ScalaElementType = {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
          argsMarker.done(ScalaElementTypes.ARG_EXPRS)
          ScalaElementTypes.ARG_EXPRS
        }
        if (ScalaTokenTypes.tLBRACE.eq(builder getTokenType)) {
          var result = BlockExpr parse builder
          if (ScalaElementTypes.BLOCK_EXPR.equals(result))
          argsMarker.done(ScalaElementTypes.ARG_EXPRS)
          ScalaElementTypes.ARG_EXPRS
        } else if (ScalaTokenTypes.tLPARENTHIS.equals(builder getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
          if (ScalaTokenTypes.tRPARENTHIS.eq(builder getTokenType)) {
            closeParent
          } else {
            var res = Exprs.parse(builder)
            if (res.eq(ScalaElementTypes.EXPRS)) {
              builder.getTokenType match {
                case ScalaTokenTypes.tRPARENTHIS => closeParent
                case _ => {
                  builder.error(") expected")
                  ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
                  argsMarker.done(ScalaElementTypes.ARG_EXPRS)
                  ScalaElementTypes.ARG_EXPRS
                }
              }
            } else {
              //builder.error("Wrong arguments")
              ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
              argsMarker.error("Wrong arguments")
              //argsMarker.done(ScalaElementTypes.ARG_EXPRS)
              ScalaElementTypes.ARG_EXPRS
            }
          }
        } else {
          argsMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      }
  }


}


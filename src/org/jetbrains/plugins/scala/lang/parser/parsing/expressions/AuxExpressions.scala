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

        if (ScalaTokenTypes.tLBRACE.eq(builder getTokenType)) {
          var result = BlockExpr parse builder
          if (ScalaElementTypes.BLOCK_EXPR.equals(result))
          argsMarker.done(ScalaElementTypes.ARG_EXPRS)
          ScalaElementTypes.ARG_EXPRS
        } else if (ScalaTokenTypes.tLPARENTHESIS.equals(builder getTokenType)){
          builder.advanceLexer
          if (ScalaTokenTypes.tRPARENTHESIS.eq(builder getTokenType)) {
            builder.advanceLexer
          } else {
            Exprs.parse(builder, null) match {
              case ScalaElementTypes.EXPRS => {
                builder.getTokenType match {
                  case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer
                  case _                             => builder.error(") expected")
                }
              }
              case _ => argsMarker.error("Arguments expected")
            }
          }
          argsMarker.done(ScalaElementTypes.ARG_EXPRS)
          ScalaElementTypes.ARG_EXPRS
        } else {
          argsMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      }
  }


}


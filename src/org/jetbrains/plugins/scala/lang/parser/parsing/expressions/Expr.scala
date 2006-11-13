package org.jetbrains.plugins.scala.lang.parser.parsing.expressions{
/**
* @author Ilya Sergey
*/
import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._

  object Expr {
  /*
  Common expression
  Default grammar
  Expr ::= ( Bindings | Id ) ‘=>’ Expr
          | Expr1               (a)
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        var exprMarker = builder.mark()
        var result = Bindings.parse(builder)

        def parseComposite: ScalaElementType = {
          result = CompositeExpr.parse(builder)
          if (ScalaElementTypes.EXPR1.equals(result)) {
            exprMarker.drop()
            ScalaElementTypes.EXPR
          } else {
            exprMarker.rollbackTo()
            ScalaElementTypes.WRONGWAY
          }
        }

        def parseTail: ScalaElementType = {
          var res = parse(builder)
          if (ScalaElementTypes.EXPR.equals(res)) {
            exprMarker.done(ScalaElementTypes.AN_FUN)
            ScalaElementTypes.EXPR
          } else {
            builder.error("Expression expected")
            exprMarker.drop()
            ScalaElementTypes.EXPR
          }
        }

        val rbMarker = builder.mark()
        var first = builder.getTokenType ;
          builder.advanceLexer; ParserUtils.rollForward(builder)
        var second = builder.getTokenType ;
          builder.advanceLexer; ParserUtils.rollForward(builder)
        rbMarker.rollbackTo()
        if (ScalaTokenTypes.tIDENTIFIER.equals(first) &&
            ScalaTokenTypes.tFUNTYPE.equals(second) ) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            ParserUtils.rollForward(builder)
          ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
            ParserUtils.rollForward(builder)
          parseTail
        } else if (ScalaElementTypes.BINDINGS.equals(result)){
          ParserUtils.rollForward(builder)
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE => {
              ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
              ParserUtils.rollForward(builder)
              parseTail
            }
            case _ => {
              exprMarker.rollbackTo()
              exprMarker = builder.mark()
              parseComposite
            }
          }
        } else {
          parseComposite
        }
      }


  }

    object Exprs {
  /*
  Expression list
  Default grammar
  Exprs ::= Expr {‘,’ Expr}
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        val exprsMarker = builder.mark()

        def subParse: ScalaElementType = {
          ParserUtils.rollForward(builder)
          builder getTokenType match {
            case ScalaTokenTypes.tCOMMA => {
              ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
              ParserUtils.rollForward(builder)
              val res1 = Expr.parse(builder)
              if (res1.equals(ScalaElementTypes.EXPR)) {
                subParse
              } else {
                builder.error("Argument expected")
                //exprsMarker.done(ScalaElementTypes.EXPRS)
                exprsMarker.drop
                ScalaElementTypes.EXPRS
              }
            }
            case _ => {
              //exprsMarker.done(ScalaElementTypes.EXPRS)
              exprsMarker.drop
              ScalaElementTypes.EXPRS
            }
          }
        }

        var result = Expr.parse(builder)
        /** Case (a) **/
        if (result.equals(ScalaElementTypes.EXPR)) {
          subParse
        }
        else {
          builder.error("Argument expected")
          //exprsMarker.done(ScalaElementTypes.EXPRS)
          exprsMarker.drop
          ScalaElementTypes.EXPRS
        //  exprsMarker.rollbackTo()
        //  ScalaElementTypes.WRONGWAY
        }
      }
  }



}
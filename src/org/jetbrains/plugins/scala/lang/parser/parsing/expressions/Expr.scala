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
  Expr ::= Bindings ‘=>’ Expr
          | Expr1               (a)
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        val exprMarker = builder.mark()

        var result = CompositeExpr.parse(builder)
        /** Case (a) **/
        if (result.equals(ScalaElementTypes.EXPR1)) {
          exprMarker.done(ScalaElementTypes.EXPR)
          ScalaElementTypes.EXPR
        }
        else {
          exprMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
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
          builder getTokenType match {
            case ScalaTokenTypes.tCOLON => {
              ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
              val res1 = Expr.parse(builder)
              if (res1.equals(ScalaElementTypes.EXPR)) {
                subParse
              } else {
                exprsMarker.rollbackTo()
                ScalaElementTypes.WRONGWAY
              }
            }
            case _ => {
              exprsMarker.done(ScalaElementTypes.EXPRS)
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
          exprsMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      }
  }



}
package org.jetbrains.plugins.scala.lang.parser.parsing.expressions
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

object CompositeExpr {
/*
Composite Expression
Default grammar
Expr1 ::= if ‘(’ Expr1 ‘)’ [NewLine] Expr [[‘;’] else Expr]
          | try ‘{’ Block ‘}’ [catch ‘{’ CaseClauses ‘}’] [finally Expr]
          | while ‘(’ Expr ‘)’ [NewLine] Expr
          | do Expr [StatementSeparator] while ‘(’ Expr ’)’
          | for (‘(’ Enumerators ‘)’ | ‘{’ Enumerators ‘}’)[NewLine] [yield] Expr
          | throw Expr
          | return [Expr]
          | [SimpleExpr ‘.’] id ‘=’ Expr                                               (b2)
          | SimpleExpr ArgumentExprs ‘=’ Expr                                         (b1)
          | PostfixExpr [‘:’ Type1]                                                   (a)
          | PostfixExpr match ‘{’ CaseClauses ‘}’
          | MethodClosure
*/

  def parse(builder : PsiBuilder) : ScalaElementType = {
    val compMarker = builder.mark()

    /***********************/
    /**** Various cases ****/
    /***********************/

    /****** case (a) *******/
    def aCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback
      var result = PostfixExpr.parse(builder)
      if (result.equals(ScalaElementTypes.POSTFIX_EXPR)) {
        builder getTokenType match {
          /*    [‘:’ Type1]   */
          case ScalaTokenTypes.tCOLON => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
            val res = Type1 parse (builder)
            res match {
              case ScalaElementTypes.TYPE1 => {
                rollbackMarker.drop()
                compMarker.done(ScalaElementTypes.EXPR1)
                ScalaElementTypes.EXPR1
              }
              case _ => {
                rollbackMarker.rollbackTo()
                ScalaElementTypes.WRONGWAY
              }
            }
          }
          case _ => {
            rollbackMarker.drop()
            compMarker.done (ScalaElementTypes.EXPR1)
            ScalaElementTypes.EXPR1
          }
        }
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    /****** cases (b1), (b2) *******/
    def bCase: ScalaElementType = {
      var rollbackMarker = builder.mark() // marker to rollback

      def assignProcess: ScalaElementType = {
        ParserUtils.eatElement(builder , ScalaTokenTypes.tASSIGN)
        ParserUtils.rollForward(builder)
        var res = Expr.parse(builder)
        if (res.eq(ScalaElementTypes.EXPR)) {
          rollbackMarker.drop()
          compMarker.done (ScalaElementTypes.EXPR1)
          ScalaElementTypes.EXPR1
        } else {
          rollbackMarker.drop()
          builder.error("Expression expected")
          compMarker.done (ScalaElementTypes.EXPR1)
          ScalaElementTypes.EXPR1
        }
      }

      if (builder.getTokenType.eq(ScalaTokenTypes.tIDENTIFIER)) {
        ParserUtils.eatElement(builder , ScalaTokenTypes.tIDENTIFIER)
        if (builder.getTokenType.eq(ScalaTokenTypes.tASSIGN)) {
          ParserUtils.rollForward(builder)
          assignProcess
        } else {
          rollbackMarker.rollbackTo()
          rollbackMarker = builder.mark()
          ParserUtils.rollForward(builder)
          var res = SimpleExpr.parse(builder)
          if (res.parsed.eq(ScalaElementTypes.SIMPLE_EXPR) &&
              ( res.endness.eq("argexprs") || res.endness.eq(".id") ) ) {
            ParserUtils.rollForward(builder)
            if (builder.getTokenType.eq(ScalaTokenTypes.tASSIGN)) {
            assignProcess
            } else {
              rollbackMarker.rollbackTo()
              ScalaElementTypes.WRONGWAY
            }
          } else {
            rollbackMarker.rollbackTo()
            ScalaElementTypes.WRONGWAY
          }
        }
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    var result = ScalaElementTypes.WRONGWAY
    /* Various variants of parsing */
    def variants(variantProcessing: => ScalaElementType) : Boolean = {
      result = variantProcessing
      result.equals(ScalaElementTypes.EXPR1)
    }

    /* Parsing function body */

    /* case (b) */
    if (variants(bCase)) result
    /* case (a) */
    else if (variants(aCase)) result
    else {
      compMarker.rollbackTo()
      ScalaElementTypes.WRONGWAY
    }

  }

}
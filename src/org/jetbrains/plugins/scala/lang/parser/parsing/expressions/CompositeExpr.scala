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
          | [SimpleExpr ‘.’] id ‘=’ Expr
          | SimpleExpr ArgumentExprs ‘=’ Expr
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



      /* Parsing function body */
      /* case (a) */
      var result = aCase
      if (result.equals(ScalaElementTypes.EXPR1)) result
      else {
        compMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }


    }


}
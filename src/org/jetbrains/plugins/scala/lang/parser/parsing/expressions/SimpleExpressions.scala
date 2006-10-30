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

/*
* Simple expression result
* Structure, that defines, what result Simple Expression parsing returned
* an it's endness ('.' id or ArgumentList)
* It is very useful for Composite Expression parsing
*/
class SimpleExprResult(val parsed: ScalaElementType, val endness: String)


/*
SIMPLE EXPRESSION
Default grammar:
SimpleExpr ::= Literal                     (d)
              | Path                       (e) 
              | ‘(’ [Expr] ‘)’               (f)
              | BlockExpr
              | new Template
              | SimpleExpr ‘.’ id           (a)
              | SimpleExpr TypeArgs        (b)
              | SimpleExpr ArgumentExprs   (c)
              | XmlExpr

*******************************************

Realized grammar:
SimpleExpr ::= Literal
              | SimpleExpr ‘.’ id

*******************************************

FIRST(SimpleExpr) = ScalaTokenTypes.tINTEGER,
           ScalaTokenTypes.tFLOAT,
           ScalaTokenTypes.kTRUE,
           ScalaTokenTypes.kFALSE,
           ScalaTokenTypes.tCHAR,
           ScalaTokenTypes.kNULL,
           ScalaTokenTypes.tSTRING_BEGIN

*/
  object SimpleExpr {

  val SIMPLE_FIRST = TokenSet.orSet(Array(BNF.tSIMPLE_FIRST, BNF.tLITERALS ))

    def parse(builder : PsiBuilder) : SimpleExprResult = {

      def closeParent: SimpleExprResult = {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
          new SimpleExprResult(ScalaElementTypes.SIMPLE_EXPR, "plain")
      }

      def subParse(stringRes: String): SimpleExprResult = {
        /* case (a) */
        if (builder.getTokenType.eq(ScalaTokenTypes.tDOT))  {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER => {
              ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
              subParse(".id")
            }
            case _ => {
              builder.error("Identifier expected")
              new SimpleExprResult(ScalaElementTypes.WRONGWAY, "wrong")
            }
          }
        /* case (b) */
        } else if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHIS) ||
                   builder.getTokenType.eq(ScalaTokenTypes.tLBRACE)) {
          var res = ArgumentExprs.parse(builder)
          if (res.eq(ScalaElementTypes.ARG_EXPRS)) subParse ("argexprs")
          else {
            builder.error("Arguments expected")
            new SimpleExprResult(ScalaElementTypes.WRONGWAY, "wrong")
          }
        /* case (c) */ 
        } else if (builder.getTokenType.eq(ScalaTokenTypes.tLSQBRACKET)) {
          var res = TypeArgs.parse(builder)
          if (res.eq(ScalaElementTypes.TYPE_ARGS)) subParse("typeargs")
          else {
            builder.error("Type argument expected")
            new SimpleExprResult(ScalaElementTypes.WRONGWAY, "wrong")
          }
        } else new SimpleExprResult(ScalaElementTypes.SIMPLE_EXPR, stringRes)
      }

      var flag = false
      var endness = "wrong"

      /* case (f) */
      if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHIS)) {
        ParserUtils.eatElement(builder,ScalaTokenTypes.tLPARENTHIS)
        ParserUtils.rollForward(builder)
        if (builder.getTokenType.eq(ScalaTokenTypes.tRPARENTHIS)) {
          closeParent
          flag = true
        } else {
          var res = Expr parse builder 
          if (res.eq(ScalaElementTypes.EXPR)) {
            ParserUtils.rollForward(builder)
            if (builder.getTokenType.eq(ScalaTokenTypes.tRPARENTHIS)) {
              closeParent
              flag = true
            } else {
              builder.error(" ) expected")
              flag = false
            }
          } else flag = false
        }
      } else {
        /* case (d) */
        var result = Literal.parse(builder)  // Literal ?
        if (!result.eq(ScalaElementTypes.WRONGWAY)) {
          flag = true // Yes, it is!
          endness = "plain"
        }
        /* case (e) */
        else { result = StableId.parse(builder) // Path ?
          if (!flag && result.equals(ScalaElementTypes.PATH)) {
            flag = true
            endness = "plain"
          }
          if (!flag && result.equals(ScalaElementTypes.STABLE_ID)) {
            flag = true
            endness = ".id"
          }
          // ... other cases
        }
      }
      if (flag) subParse(endness)
        else new SimpleExprResult(ScalaElementTypes.WRONGWAY , "wrong")
    }


  }

/*
PREFIX EXPRESSION
Default grammar
PrefixExpr ::= [ ‘-’ | ‘+’ | ‘~’ | ‘!’ ] SimpleExpr

***********************************************

Realized grammar:
PrefixExpr ::= [ ‘-’ | ‘+’ | ‘~’ | ‘!’ ] SimpleExpr

*******************************************

FIRST(PrefixExpression) = ScalaTokenTypes.tPLUS
                          ScalaTokenTypes.tMINUS
                          ScalaTokenTypes.tTILDA
                          ScalaTokenTypes.tNOT
     union                SimpleExpression.FIRST
*/
  object PrefixExpr {

    val PREFIX_FIRST = TokenSet.orSet(Array(SimpleExpr.SIMPLE_FIRST, BNF.tPREFIXES ))

    def parse(builder : PsiBuilder) : ScalaElementType = {

      val marker = builder.mark()
      var result = ScalaElementTypes.PREFIX_EXPR

      builder getTokenType match {
        case ScalaTokenTypes.tPLUS
             | ScalaTokenTypes.tMINUS
             | ScalaTokenTypes.tTILDA
             | ScalaTokenTypes.tNOT => {
               ParserUtils.eatElement(builder, ScalaElementTypes.PREFIX)
               if (SimpleExpr.SIMPLE_FIRST.contains(builder.getTokenType)) {
                 result = (SimpleExpr  parse(builder)).parsed
               } else {
                builder.error("Wrong prefix expression!")
                result = ScalaElementTypes.WRONGWAY
               }
             }
        case _ => result = (SimpleExpr parse(builder)).parsed
      }
      marker.done(ScalaElementTypes.PREFIX_EXPR)
      if (result.equals(ScalaElementTypes.SIMPLE_EXPR)) ScalaElementTypes.PREFIX_EXPR
      else result
    }
  }

/*
INFIX EXPRESSION
Default grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

Realized grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

FIRST(InfixExpression) =  PrefixExpression.FIRST

*/
  object InfixExpr {

    val INFIX_FIRST = PrefixExpr.PREFIX_FIRST

    def parse(builder : PsiBuilder) : ScalaElementType = {
      val marker = builder.mark()
      var result = PrefixExpr parse(builder)

      def subParse(currentMarker: PsiBuilder.Marker) : ScalaElementType = {
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            val rollbackMarker = builder.mark() //for rollback
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            ParserUtils.rollForward(builder)
            var res = PrefixExpr parse(builder)
            if (res.equals(ScalaElementTypes.PREFIX_EXPR)) {
              rollbackMarker.drop()
              val nextMarker = currentMarker.precede()
              currentMarker.done(ScalaElementTypes.INFIX_EXPR)
              subParse(nextMarker)
            } else {
              rollbackMarker.rollbackTo()
              currentMarker.drop()
              ScalaElementTypes.INFIX_EXPR
            }
          }
          case _ => {
            currentMarker.drop()
            ScalaElementTypes.INFIX_EXPR
          }
        }
      }

      if (result.equals(ScalaElementTypes.PREFIX_EXPR)) {
        val nextMarker = marker.precede()
        marker.done(ScalaElementTypes.INFIX_EXPR)
        result = subParse(nextMarker)
        result
      }
      else {
        builder.error("Wrong infix expression!")
        marker.done(ScalaElementTypes.INFIX_EXPR)
        result
      }
    }
  }
/*
POSTFIX EXPRESSION
Default grammar:
PostfixExpr ::= InfixExpr [id [NewLine]]

***********************************************

Realized grammar:                                             
PostfixExpr ::= InfixExpr [id [NewLine]]

***********************************************

FIRST(PostfixExpression) =  InffixExpression.FIRST
*/
  object PostfixExpr {

    val POSTFIX_FIRST = InfixExpr.INFIX_FIRST

    def parse(builder : PsiBuilder) : ScalaElementType = {
      val marker = builder.mark()
      var result = InfixExpr parse(builder)
      if (result.equals(ScalaElementTypes.INFIX_EXPR)) {
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            ParserUtils.rollForward(builder)
          }
          case _ =>
        }
        marker.done(ScalaElementTypes.POSTFIX_EXPR)
        ScalaElementTypes.POSTFIX_EXPR
      }
      else {
        builder.error("Wrong postfix expression!")
        marker.done(ScalaElementTypes.POSTFIX_EXPR)
        result
      }
    }

  }

}


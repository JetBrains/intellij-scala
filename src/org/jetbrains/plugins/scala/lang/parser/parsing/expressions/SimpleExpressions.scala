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
              | BlockExpr                  (g)
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

  val tLITERALS: TokenSet = TokenSet.create(
    Array( ScalaTokenTypes.tINTEGER,
           ScalaTokenTypes.tFLOAT,
           ScalaTokenTypes.kTRUE,
           ScalaTokenTypes.kFALSE,
           ScalaTokenTypes.tCHAR,
           ScalaTokenTypes.kNULL,
           ScalaTokenTypes.tSTRING_BEGIN
    )
  )

  val tSIMPLE_FIRST: TokenSet = TokenSet.create(
    Array( ScalaTokenTypes.tIDENTIFIER,
           ScalaTokenTypes.kTHIS,
           ScalaTokenTypes.kSUPER,
           ScalaTokenTypes.tLPARENTHIS,
           ScalaTokenTypes.kNEW
    )
  )

  //val SIMPLE_FIRST = TokenSet.orSet(Array(BNF.tSIMPLE_FIRST, BNF.tLITERALS ))
  val SIMPLE_FIRST = TokenSet.orSet(Array(tSIMPLE_FIRST, tLITERALS ))

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

      // NEW!!!!!!!!!!!!!!!!!!!!!!!!!
      /* case (g) */      
      if (builder.getTokenType.eq(ScalaTokenTypes.tLBRACE))  {
          val res3 = BlockExpr.parse(builder)
          if (res3.eq(ScalaElementTypes.BLOCK_EXPR)){
            endness = "plain"
            flag = true
          } else {
            flag = false
          }
      // NEW!!!!!!!!!!!!!!!!!!!!!!!!!
      /* case (f) */
      } else if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHIS)) {
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
              endness = "plain"
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


    val tPREFIXES: TokenSet = TokenSet.create(
      Array(
        ScalaTokenTypes.tPLUS,
        ScalaTokenTypes.tMINUS,
        ScalaTokenTypes.tTILDA,
        ScalaTokenTypes.tNOT
      )
    )
    //val PREFIX_FIRST = TokenSet.orSet(Array(SimpleExpr.SIMPLE_FIRST, BNF.tPREFIXES ))
    val PREFIX_FIRST = TokenSet.orSet(Array(SimpleExpr.SIMPLE_FIRST, tPREFIXES ))

    def parse(builder : PsiBuilder) : ScalaElementType = {

      val marker = builder.mark()
      var result = ScalaElementTypes.PREFIX_EXPR

      builder getTokenType match {
        case ScalaTokenTypes.tPLUS
             | ScalaTokenTypes.tMINUS
             | ScalaTokenTypes.tTILDA
             | ScalaTokenTypes.tNOT
             | ScalaTokenTypes.tAND => {
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


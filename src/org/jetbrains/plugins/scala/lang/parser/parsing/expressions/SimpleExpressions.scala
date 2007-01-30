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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._

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
            | new Template               (h)
            | SimpleExpr ‘.’ id           (a)
            | SimpleExpr TypeArgs        (b)
            | SimpleExpr ArgumentExprs   (c)
            | XmlExpr

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

  val tLITERALS: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tINTEGER,
          ScalaTokenTypes.tFLOAT,
          ScalaTokenTypes.kTRUE,
          ScalaTokenTypes.kFALSE,
          ScalaTokenTypes.tCHAR,
          ScalaTokenTypes.kNULL))

  val tSIMPLE_FIRST: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER,
          ScalaTokenTypes.kTHIS,
          ScalaTokenTypes.kSUPER,
          ScalaTokenTypes.tLPARENTHIS,
          ScalaTokenTypes.kNEW))

  //val SIMPLE_FIRST = TokenSet.orSet(Array(BNF.tSIMPLE_FIRST, BNF.tLITERALS ))
  val SIMPLE_FIRST = TokenSet.orSet(Array(tSIMPLE_FIRST, tLITERALS))

  def parse(builder: PsiBuilder, marker: PsiBuilder.Marker, braced: Boolean): SimpleExprResult = {

    var deepCount = 1
    var inBraces = braced

    def closeParent: SimpleExprResult = {
      ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
      new SimpleExprResult(ScalaElementTypes.SIMPLE_EXPR, "plain")
    }

    def subParse(_res: ScalaElementType, stringRes: String, simpleMarker1: PsiBuilder.Marker): SimpleExprResult = {

      /* case (a) */
      if (builder.getTokenType.eq(ScalaTokenTypes.tDOT))  {

        var nextMarker = simpleMarker1.precede()
        simpleMarker1.done(ScalaElementTypes.SIMPLE_EXPR)

        ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            deepCount = deepCount + 1
            subParse(ScalaElementTypes.SIMPLE_EXPR, ".id", nextMarker)
          }
          case _ => {
            builder.error("Identifier expected")
            nextMarker.done(ScalaElementTypes.SIMPLE_EXPR)
            new SimpleExprResult(ScalaElementTypes.WRONGWAY, "wrong")
          }
        }
        /* case (b) */
      } else if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHIS) ||
      builder.getTokenType.eq(ScalaTokenTypes.tLBRACE)) {

        var nextMarker = simpleMarker1.precede()
        simpleMarker1.done(ScalaElementTypes.SIMPLE_EXPR)

        var res = ArgumentExprs.parse(builder)
        if (res.eq(ScalaElementTypes.ARG_EXPRS)) {
          deepCount = deepCount + 1
          subParse(ScalaElementTypes.SIMPLE_EXPR, "argexprs", nextMarker)
        }
        else {
          builder.error("Arguments expected")
          nextMarker.done(ScalaElementTypes.SIMPLE_EXPR)
          new SimpleExprResult(ScalaElementTypes.WRONGWAY, "wrong")
        }
        /* case (c) */
      } else if (builder.getTokenType.eq(ScalaTokenTypes.tLSQBRACKET)) {

        var nextMarker = simpleMarker1.precede()
        simpleMarker1.done(ScalaElementTypes.SIMPLE_EXPR)

        var res = TypeArgs.parse(builder)
        if (res.eq(ScalaElementTypes.TYPE_ARGS)) {
          deepCount = deepCount + 1
          subParse(ScalaElementTypes.SIMPLE_EXPR, "typeargs", nextMarker)
        }
        else {
          builder.error("Type argument expected")
          nextMarker.done(ScalaElementTypes.SIMPLE_EXPR)
          new SimpleExprResult(ScalaElementTypes.WRONGWAY, "wrong")
        }
      } else {
        if (deepCount > 1 || inBraces) {
          simpleMarker1.done(ScalaElementTypes.SIMPLE_EXPR)
        }
        else {
          simpleMarker1.drop()
        }
        new SimpleExprResult(_res, stringRes)
      }
    }

    var flag = false
    var endness = "wrong"
    var result = ScalaElementTypes.WRONGWAY

    var simpleMarker = marker match {
      case null => builder.mark()
      case marker => marker
    }

    if (marker == null) {
      /* case (h) */
      if (ScalaTokenTypes.kNEW.equals(builder.getTokenType))  {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kNEW)
        Template.parseBody(builder)
        simpleMarker.done(ScalaElementTypes.SIMPLE_EXPR)
        simpleMarker = builder.mark()
        result = ScalaElementTypes.TEMPLATE
        endness = "plain"
        flag = true
      }
      /* case (g) */
      else if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType))  {
        val res3 = BlockExpr.parse(builder)
        if (res3.eq(ScalaElementTypes.BLOCK_EXPR)){
          result = res3
          endness = "plain"
          flag = true
        } else {
          flag = false
        }
      }
      /* case (f) */
      else if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHIS)) {
        val unitMarker = builder.mark()
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
        if (builder.getTokenType.eq(ScalaTokenTypes.tRPARENTHIS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
          unitMarker.done(ScalaElementTypes.UNIT)
          result = ScalaElementTypes.UNIT
          flag = true
        } else {
          unitMarker.drop()
          var res = Expr parse builder
          if (res.eq(ScalaElementTypes.EXPR)) {
            if (builder.getTokenType.eq(ScalaTokenTypes.tRPARENTHIS)) {
              closeParent
              inBraces = true
              result = res
              endness = "plain"
              flag = true
            } else {
              builder.error(" Wrong expression")
              ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
              inBraces = true
              new SimpleExprResult(ScalaElementTypes.SIMPLE_EXPR, "plain")
              result = res
              endness = "plain"
              flag = true
            }
          } else {
            flag = false
          }
        }
      } else {
        /* case (d) */
        var result1 = Literal.parse(builder)  // Literal ?
        if (! result1.equals(ScalaElementTypes.WRONGWAY)) {
          result = result1
          flag = true // Yes, it is!
          endness = "plain"
        }
        /* case (e) */
        else { result1 = StableId.parse(builder) // Path ?
        if (! flag && (result1.equals(ScalaElementTypes.PATH)
        || result1.equals(ScalaElementTypes.STABLE_ID_ID))) {
          result = result1
          flag = true
          endness = "plain"
        }
        if (! flag && result1.equals(ScalaElementTypes.STABLE_ID)) {
          result = result1
          flag = true
          endness = ".id"
        }
        }
      }
      if (flag) {
        subParse(result, endness, simpleMarker)
      }
      else {
        simpleMarker.drop()
        new SimpleExprResult(ScalaElementTypes.WRONGWAY, "wrong")
      }
    } else {
      subParse(ScalaElementTypes.EXPR, "plain", simpleMarker)
    }

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


  val tPREFIXES: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))
  //val PREFIX_FIRST = TokenSet.orSet(Array(SimpleExpr.SIMPLE_FIRST, BNF.tPREFIXES ))
  val PREFIX_FIRST = TokenSet.orSet(Array(SimpleExpr.SIMPLE_FIRST, tPREFIXES))

  def parse(builder: PsiBuilder): ScalaElementType = {

    val marker = builder.mark()
    var result = ScalaElementTypes.PREFIX_EXPR
    var isPrefix = false

    builder getTokenText match {
      case "+"
      | "-"
      | "~"
      | "!"
      | "&" => {
        ParserUtils.eatElement(builder, ScalaElementTypes.PREFIX)
        isPrefix = true
        if (SimpleExpr.SIMPLE_FIRST.contains(builder.getTokenType)) {
          result = (SimpleExpr.parse(builder, null, false)).parsed
        } else {
          builder.error("Wrong prefix expression!")
          result = ScalaElementTypes.WRONGWAY
        }
      }
      case _ => result = (SimpleExpr.parse(builder, null, false)).parsed
    }
    if (isPrefix) {
      result = ScalaElementTypes.PREFIX_EXPR
      marker.done(ScalaElementTypes.PREFIX_EXPR)
    } else marker.drop()
    result
  }

  def exprOrArgsParse(builder: PsiBuilder): ScalaElementType = {
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHIS => {
        var argsMarker = builder.mark()
        var exprsMarker = builder.mark()
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
        if (ScalaTokenTypes.tRPARENTHIS.eq(builder getTokenType)) {
          argsMarker.drop()
          exprsMarker.done(ScalaElementTypes.UNIT)
          ScalaElementTypes.UNIT
        } else {
          var result = Expr.parse(builder)

          def closeParent(elem: ScalaElementType): ScalaElementType = {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
            argsMarker.done(elem)
            elem
          }

          if (result.equals(ScalaElementTypes.EXPR)) {
            builder.getTokenType match {
              case ScalaTokenTypes.tCOLON |
              ScalaTokenTypes.tCOMMA => {
                val res = Exprs.parse(builder, exprsMarker)
                if (res.equals(ScalaElementTypes.EXPRS)) {
                  builder.getTokenType match {
                    case ScalaTokenTypes.tRPARENTHIS => {
                      closeParent(ScalaElementTypes.ARG_EXPRS)
                    }
                    case _ => {
                      builder.error(") expected")
                      ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
                      argsMarker.done(ScalaElementTypes.ARG_EXPRS)
                      ScalaElementTypes.ARG_EXPRS
                    }
                  }
                } else {
                  ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
                  argsMarker.error("Wrong expression")
                  ScalaElementTypes.ARG_EXPRS
                }
              }
              case ScalaTokenTypes.tRPARENTHIS => {
                exprsMarker.drop()
                ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
                SimpleExpr.parse(builder, argsMarker, true).parsed
              }
              case _ => {
                builder.error(") expected")
                exprsMarker.drop()
                ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
                argsMarker.done(ScalaElementTypes.SIMPLE_EXPR)
                ScalaElementTypes.SIMPLE_EXPR
              }
            }

          } else {
            exprsMarker.drop()
            builder.error("Wrong expression")
            ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
            argsMarker.done(ScalaElementTypes.SIMPLE_EXPR)
            ScalaElementTypes.SIMPLE_EXPR
          }
        }
      }
      case ScalaTokenTypes.tLBRACE => BlockExpr.parse(builder)
      case _ => this.parse(builder)
    }
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
object InfixExpr extends InfixTemplate(ScalaElementTypes.INFIX_EXPR,
PrefixExpr.parse,
PrefixExpr.exprOrArgsParse){
  val INFIX_FIRST = PrefixExpr.PREFIX_FIRST
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

  def parse(builder: PsiBuilder): ScalaElementType = {
    val marker = builder.mark()

    var result = InfixExpr parse (builder)
    var isPostfix = false
    if (! result.equals(ScalaElementTypes.WRONGWAY)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER  if { // A bug with method closure
          val rbMarker = builder.mark()
          builder.advanceLexer
          var flag = ! ScalaTokenTypes.tDOT.equals(builder.getTokenType)
          rbMarker.rollbackTo()
          flag
        } => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          isPostfix = true
          // Real need!
          ParserUtils.rollForward(builder) //Warning!
        }
        case _ =>
      }
      if (isPostfix) {
        marker.done(ScalaElementTypes.POSTFIX_EXPR)
        ScalaElementTypes.POSTFIX_EXPR
      }
      else {
        marker.drop()
        result
      }
    }
    else {
      builder.error("Wrong postfix expression!")
      marker.done(ScalaElementTypes.POSTFIX_EXPR)
      result
    }
  }

}


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
import org.jetbrains.plugins.scala.lang.lexer._
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
            | ( [Expr] )               (f)
            | BlockExpr                  (g)
            | new Template               (h)
            | SimpleExpr . id           (a)
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
      ScalaTokenTypes.tLPARENTHESIS,
      ScalaTokenTypes.kNEW))

  //val SIMPLE_FIRST = TokenSet.orSet(Array(BNF.tSIMPLE_FIRST, BNF.tLITERALS ))
  val SIMPLE_FIRST = TokenSet.orSet(Array(tSIMPLE_FIRST, tLITERALS))

  def closeMarker(res: String, marker: PsiBuilder.Marker) = res match {
    case "parenthesised" => marker.done(ScalaElementTypes.PARENT_EXPR)
    case ".id" => marker.done(ScalaElementTypes.PROPERTY_SELECTION)
    case "id" => marker.drop
    case "this" => marker.done(ScalaElementTypes.THIS_REFERENCE_EXPRESSION)
    case "argexprs" => marker.done(ScalaElementTypes.METHOD_CALL)
    case "typeargs" => marker.done(ScalaElementTypes.GENERIC_CALL)
    case _ => marker.done(ScalaElementTypes.SIMPLE_EXPR)
  }

  def parse(builder: PsiBuilder, marker: PsiBuilder.Marker, braced: Boolean): SimpleExprResult = {

    var deepCount = 0
    var inBraces = braced

    def closeParent: SimpleExprResult = {
      ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
      new SimpleExprResult(ScalaElementTypes.SIMPLE_EXPR, "plain")
    }

    def subParse(_res: ScalaElementType, stringRes: String, simpleMarker1: PsiBuilder.Marker): SimpleExprResult = {

      /* case (a) */
      if (builder.getTokenType.eq(ScalaTokenTypes.tDOT))  {

        var nextMarker = simpleMarker1.precede()
        closeMarker(stringRes, simpleMarker1)

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
      } else if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHESIS) ||
      builder.getTokenType.eq(ScalaTokenTypes.tLBRACE)) {

        var nextMarker = simpleMarker1.precede()
        closeMarker(stringRes, simpleMarker1)

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
        closeMarker(stringRes, simpleMarker1)

        var res = TypeArgs.parse(builder)
        if (res) {
          deepCount = deepCount + 1
          subParse(ScalaElementTypes.SIMPLE_EXPR, "typeargs", nextMarker)
        }
        else {
          builder.error("Type argument expected")
          nextMarker.done(ScalaElementTypes.SIMPLE_EXPR)
          new SimpleExprResult(ScalaElementTypes.WRONGWAY, "wrong")
        }
      } else {
        if (deepCount > 0 /*|| inBraces*/) {
          closeMarker(stringRes, simpleMarker1)
        } else {
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
        if (BNF.firstTemplateParents.contains(builder.getTokenType)){
          ClassParents parse builder
          if (BNF.firstTemplateBody.contains(builder.getTokenType)){
            TemplateBody parse builder
          }
        } else {
          TemplateBody parse builder
        }
        simpleMarker.done(ScalaElementTypes.NEW_TEMPLATE)
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
      else if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHESIS)) {
        val unitMarker = builder.mark()
        val exprsMarker = builder.mark
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHESIS)
        if (builder.getTokenType.eq(ScalaTokenTypes.tRPARENTHESIS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
          unitMarker.done(ScalaElementTypes.UNIT)
          exprsMarker.drop
          result = ScalaElementTypes.UNIT
          flag = true
        } else {
          unitMarker.drop()
          val res = Exprs parse (builder,null)
          //if (res.eq(ScalaElementTypes.EXPRS)) {
            if (builder.getTokenType.eq(ScalaTokenTypes.tRPARENTHESIS)) {
              closeParent
//              inBraces = true
              result = res
              endness = "parenthesised"
              flag = true
            } else if (builder.getTokenType.equals(ScalaTokenTypes.tCOMMA)){
              builder.advanceLexer
              if (builder.getTokenType.equals(ScalaTokenTypes.tRPARENTHESIS))
              {
                closeParent
                result = res
                endness = "parenthesised"
                flag = true
              }
              else
              {
                builder error ") expected"
                new SimpleExprResult(ScalaElementTypes.SIMPLE_EXPR, "plain")
                result = res
                endness = "plain"
                flag = true
              }
            } else {
              builder.error(" Expression expected")
//              inBraces = true
              new SimpleExprResult(ScalaElementTypes.SIMPLE_EXPR, "plain")
              result = res
              endness = "plain"
              flag = true
            }
          exprsMarker.done(ScalaElementTypes.EXPRS)
          /*} else {
            flag = false
          }*/
        }
      } else {
        /* case (d) */
        var result1 = Literal.parse(builder)  // Literal ?
        if (result1) {
          result = ScalaElementTypes.LITERAL
          flag = true // Yes, it is!
          endness = "plain"
        }
        /* case (e) */
        else {
          // stable id parsing
          // TODO add 'super' case parsing
          def thisReferenfceParsing(marker: PsiBuilder.Marker) = {
            if (ScalaTokenTypes.kTHIS.equals(builder.getTokenType())) {
              builder.advanceLexer()
              marker.done(ScalaElementTypes.THIS_REFERENCE_EXPRESSION)
              endness = "this"
              flag = true;
              result = ScalaElementTypes.THIS_REFERENCE_EXPRESSION
            } else {
              marker.drop()
            }
          }
          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType())) {
            val marker = builder.mark()
            builder.advanceLexer()
            val newMarker = marker.precede
            result = ScalaElementTypes.REFERENCE_EXPRESSION
            endness = "id"
            flag = true;
            marker.done(ScalaElementTypes.REFERENCE_EXPRESSION)
            //marker.drop
            thisReferenfceParsing(newMarker)
          } else if (ScalaTokenTypes.kTHIS.equals(builder.getTokenType())) {
            val marker = builder.mark
            thisReferenfceParsing(marker)
          }
        }
      }
      if (flag) {
        subParse(result, endness, simpleMarker)
      } else {
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
PrefixExpr ::= [ - | + | ~ | ! ] SimpleExpr

***********************************************

Realized grammar:
PrefixExpr ::= [ - | + | ~ | ! ] SimpleExpr

*******************************************

FIRST(PrefixExpression) = ScalaTokenTypes.tPLUS
                        ScalaTokenTypes.tMINUS
                        ScalaTokenTypes.tTILDA
                        ScalaTokenTypes.tNOT
   union                SimpleExpression.FIRST
*/
/*object PrefixExpr {


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
      case ScalaTokenTypes.tLPARENTHESIS => {
        var argsMarker = builder.mark()
        var exprsMarker = builder.mark()
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHESIS)
        if (ScalaTokenTypes.tRPARENTHESIS.eq(builder getTokenType)) {
          argsMarker.drop()
          exprsMarker.done(ScalaElementTypes.UNIT)
          ScalaElementTypes.UNIT
        } else {
          var result = Expr.parse(builder)

          def closeParent(elem: ScalaElementType): ScalaElementType = {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
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
                    case ScalaTokenTypes.tRPARENTHESIS => {
                      closeParent(ScalaElementTypes.ARG_EXPRS)
                    }
                    case _ => {
                      builder.error(") expected")
                      argsMarker.done(ScalaElementTypes.ARG_EXPRS)
                      ScalaElementTypes.ARG_EXPRS
                    }
                  }
                } else {
                  argsMarker.error("Wrong expression")
                  ScalaElementTypes.ARG_EXPRS
                }
              }
              case ScalaTokenTypes.tRPARENTHESIS => {
                exprsMarker.drop()
                ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
                SimpleExpr.parse(builder, argsMarker, true).parsed
              }
              case _ => {
                builder.error(") expected")
                exprsMarker.drop()
                argsMarker.done(ScalaElementTypes.SIMPLE_EXPR)
                ScalaElementTypes.SIMPLE_EXPR
              }
            }

          } else {
            exprsMarker.drop()
            builder.error("Expression expected")
            argsMarker.done(ScalaElementTypes.SIMPLE_EXPR)
            ScalaElementTypes.SIMPLE_EXPR
          }
        }
      }
      case _ => this.parse(builder)
    }
  }


} */

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
//object InfixExpr extends InfixTemplate(ScalaElementTypes.INFIX_EXPR, PrefixExpr.parse, PrefixExpr.exprOrArgsParse){
//  val INFIX_FIRST = PrefixExpr.PREFIX_FIRST
//}

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
/*object PostfixExpr {

  //val POSTFIX_FIRST = InfixExpr.INFIX_FIRST

  def parse(builder: PsiBuilder): ScalaElementType = {
    val marker = builder.mark()

    var result = InfixExpr parse (builder)
    var isPostfix = false
    if (! result.equals(ScalaElementTypes.WRONGWAY)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER  if { // A bug with method closure (fixed)
          val rbMarker = builder.mark()
          builder.advanceLexer
          var flag = ! ScalaTokenTypes.tDOT.equals(builder.getTokenType)
          rbMarker.rollbackTo()
          flag
        } => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          isPostfix = true
          //  Fixed 14.02.07 - be careful
          //          ParserUtils.rollForward(builder)
          /*
                    val rb = builder.mark()
                    var dropped = false
                    if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)){
                      builder.advanceLexer
                      if (BNF.firstDef.contains(builder.getTokenType)){
                        rb.rollbackTo()
                      } else {
                        rb.drop()
                      }
                      dropped = true
                    }
                    if (!dropped) rb.drop
          */


        }
        case _ =>
      }
      if (isPostfix) {
        marker.done(ScalaElementTypes.POSTFIX_EXPR)
        ScalaElementTypes.POSTFIX_EXPR
      }
      else {
        marker.drop()
        if (result) ScalaElementTypes.INFIX_EXPR
        else ScalaElementTypes.WRONGWAY
      }
    }
    else {
      builder.error("Wrong postfix expression!")
      marker.done(ScalaElementTypes.POSTFIX_EXPR)
      if (result) ScalaElementTypes.INFIX_EXPR
      else ScalaElementTypes.WRONGWAY
    }
  }

} */


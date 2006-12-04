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


  object ResultExpr {
  /*
  Result expression
  Default grammar
  Expr ::= ( Bindings | Id ) ‘=>’ Expr
          | Expr1               (a)
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        var exprMarker = builder.mark()
        var result = Bindings.parse(builder)

        //Console.println(result)

        def parseComposite: ScalaElementType = {
          result = CompositeExpr.parse(builder)
          if (!ScalaElementTypes.WRONGWAY.equals(result)) {
            //exprMarker.done(ScalaElementTypes.EXPR1)
            exprMarker.drop
            ScalaElementTypes.EXPR1
          } else {
//            builder.error("Wrong result expression")
//            exprMarker.done(ScalaElementTypes.RESULT_EXPR)
            exprMarker.rollbackTo()
            ScalaElementTypes.RESULT_EXPR          }
        }

        def parseTail: ScalaElementType = {

          if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)){
            exprMarker.done(ScalaElementTypes.RESULT_EXPR)
            ScalaElementTypes.RESULT_EXPR  
          } else {
            var res = Block.parse (builder , false)

// ACHTUNG!!!
            if (!ScalaElementTypes.WRONGWAY.equals(res)) {
              exprMarker.done(ScalaElementTypes.RESULT_EXPR)
              ScalaElementTypes.RESULT_EXPR
            } else {
              exprMarker.rollbackTo()
              ScalaElementTypes.RESULT_EXPR
            }
          }
        }

        val rbMarker = builder.mark()
        var first = builder.getTokenType ;
          builder.advanceLexer;
        var second = builder.getTokenType ;
        rbMarker.rollbackTo()

        if (ScalaTokenTypes.tIDENTIFIER.equals(first) &&
            ScalaTokenTypes.tFUNTYPE.equals(second) ) {

          /* Let's kick it! */
          builder.getTokenType
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          builder.getTokenType
          ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
          parseTail
        } else if (ScalaTokenTypes.tIDENTIFIER.equals(first) &&
                   ScalaTokenTypes.tCOLON.equals(second) ){
           //var rbMarker = builder.mark()
           builder.getTokenType
           ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
           builder.getTokenType
           ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
           var res3 = Type1 parse builder
           if (ScalaElementTypes.TYPE1.equals(res3)) {
             if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)){
               ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
               parseTail
             } else {
               builder.error("=> expected")
               exprMarker.done(ScalaElementTypes.RESULT_EXPR)
               ScalaElementTypes.RESULT_EXPR
             }
           } else {
             builder.error("Wrong type")
             exprMarker.done(ScalaElementTypes.RESULT_EXPR)
             ScalaElementTypes.RESULT_EXPR
           }
        } else if (ScalaElementTypes.BINDINGS.equals(result)){
          ParserUtils.rollForward(builder)
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE => {
              ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
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


  object Expr {
  /*
  Common expression
  Default grammar
  Expr ::= ( Bindings | Id ) ‘=>’ Expr
          | Expr1               (a)
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        var exprMarker = builder.mark()
        var result = ScalaElementTypes.WRONGWAY

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
          if (!ScalaElementTypes.WRONGWAY.equals(res)) {
            exprMarker.done(ScalaElementTypes.AN_FUN)
            ScalaElementTypes.EXPR
          } else {
            builder.error("Expression expected")
            exprMarker.done(ScalaElementTypes.AN_FUN)
            ScalaElementTypes.EXPR
          }
        }

        val rbMarker = builder.mark()

        var first = if (builder.getTokenType != null) builder.getTokenType
                    else ScalaTokenTypes.tWRONG
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        var second = if (builder.getTokenType != null) builder.getTokenType
                    else ScalaTokenTypes.tWRONG
        ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)

        if (ScalaTokenTypes.tIDENTIFIER.equals(first) &&
            ScalaTokenTypes.tFUNTYPE.equals(second) ) {
          rbMarker.drop()
          parseTail
        } else if (
          {
            rbMarker.rollbackTo()
            result = Bindings.parse(builder)
            ScalaElementTypes.BINDINGS.equals(result)
          }
        ){
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
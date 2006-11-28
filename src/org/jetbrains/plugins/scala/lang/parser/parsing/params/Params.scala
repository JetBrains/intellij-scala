package org.jetbrains.plugins.scala.lang.parser.parsing.top.params {

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.11.2006
 * Time: 12:43:03
 */
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.Constr
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint

 
    class ParamClauses[T <: Param] (param : T) extends ConstrUnpredict {
//      override def getElementType = ScalaElementTypes.PARAM_CLAUSES

          def checkForParamClause[T <: Param](param : T, first : IElementType, second : IElementType, third : IElementType) : Boolean = {
            if (first == null || second == null || third == null) return false

            var a = first
            var b = second
            var c = third
            if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
              a = b
              b = c
            }

            a.equals(ScalaTokenTypes.tLPARENTHIS) && (param.first.contains(b) || b.equals(ScalaTokenTypes.tRPARENTHIS))

          }

          def checkForImplicit(first : IElementType, second : IElementType, third : IElementType) : Boolean = {
            if (first == null || second == null || third == null) return false

            var a = first
            var b = second
            var c = third
            if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
              a = b
              b = c
            }

            a.equals(ScalaTokenTypes.tLPARENTHIS) && b.equals(ScalaTokenTypes.kIMPLICIT)

          }
      

      override def parseBody(builder : PsiBuilder) : Unit = {
        var paramClausesMarker = builder.mark
        var chooseParsingWay = builder.mark

        var first = builder.getTokenType()
        builder.advanceLexer

        var second = builder.getTokenType()
        builder.advanceLexer

        var third = builder.getTokenType()
        //builder.advanceLexer

        var numberParamClauses = 0;

        //it is possible to cyclic
        while (checkForParamClause[T](param, first, second, third)) {
          chooseParsingWay.rollbackTo()
          new ParamClause[T](param).parse(builder)
          numberParamClauses = numberParamClauses + 1
          chooseParsingWay = builder.mark()

          first = builder.getTokenType()
          builder.advanceLexer
          second = builder.getTokenType()
          builder.advanceLexer
          third = builder.getTokenType()
        }

        chooseParsingWay.rollbackTo()

        if (checkForImplicit(first, second, third)) {
          new ImplicitEnd[T](param).parse(builder)
        }

        //chooseParsingWay.drop()

        DebugPrint println ("param clauses: " + numberParamClauses)

        if (numberParamClauses > 1) paramClausesMarker.done(ScalaElementTypes.PARAM_CLAUSES)
        else paramClausesMarker.drop()
      }
    }

 class ParamClause[T <: Param] (param : T) extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.PARAM_CLAUSE

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
      }

      if (ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)

      } else {
        builder error "expected '('"
        return
      }

      if (param.first.contains(builder.getTokenType)){
        ParserUtils.listOfSmth(builder, param, ScalaTokenTypes.tCOMMA, ScalaElementTypes.PARAMS)
      }

      if (ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
      } else {
        builder error "expected ')'"
        return
      }
    }
  }

   class ImplicitEnd[T <: Param] (param : T) extends Constr{
     override def getElementType : IElementType = ScalaElementTypes.PARAM_CLAUSE

      override def parseBody (builder : PsiBuilder) : Unit = {
         if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
        }

        if (ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
        } else {
          builder.error("expected '('")
          return
        }

        if (ScalaTokenTypes.kIMPLICIT.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kIMPLICIT)
        } else {
          builder.error("expected 'implicit'")
          return
        }

        if (param.first.contains(builder.getTokenType())) {
          ParserUtils.listOfSmth(builder, param, ScalaTokenTypes.tCOMMA, ScalaElementTypes.PARAM_CLAUSE)
        } else {
          builder.error("expected parameter or list of parameters")
          return
        }

        if (ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
        } else {
          builder.error("expected ')'")
          return
        }
      }
    }

  class Param extends ConstrItem {
    override def getElementType : IElementType = ScalaElementTypes.PARAM

    override def first : TokenSet = BNF.firstParam

    override def parseBody(builder : PsiBuilder) : Unit = {
       if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      } else {
        builder error "expected identifier"
        return
      }

      if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
      } else {
        builder error "expected ':'"
        return
      }

      if (BNF.firstParamType.contains(builder.getTokenType)){
        ParamType parse builder
      } else {
        builder error "expected type parameter"
        return
      }
    }
  }

  object ParamType extends ConstrUnpredict {
//    override def getElementType : IElementType = ScalaElementTypes.PARAM_TYPE

    override def parseBody(builder : PsiBuilder) : Unit = {
      var paramTypeMarker = builder.mark
      var isParamType = false;

      if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
         isParamType = true;
      }

      if (BNF.firstType.contains(builder.getTokenType)) {
         Type parse builder
         isParamType = false;
      } else {
        builder error "expected type declaration"
      }

      if (ScalaTokenTypes.tSTAR.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.tSTAR)
         isParamType = true;
      }

      if (isParamType) paramTypeMarker.done(ScalaElementTypes.PARAM_TYPE)
      else paramTypeMarker.done(ScalaElementTypes.TYPE)
    }
  }
}
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

 object TypeParam extends ConstrItem {
      override def getElementType = ScalaElementTypes.FUN_TYPE_PARAM

      override def first = BNF.firstTypeParam

      override def parseBody(builder : PsiBuilder) : Unit = {
        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        } else builder.error("expected identifier")

        if (builder.getTokenType.equals(ScalaTokenTypes.tLOWER_BOUND)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLOWER_BOUND)
          Type.parse(builder)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tUPPER_BOUND)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tUPPER_BOUND)
          Type.parse(builder)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tVIEW)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tVIEW)
          Type.parse(builder)
        }
      }
    }


    class ParamClauses[T <: Param] (param : T) extends Constr {
      override def getElementType = ScalaElementTypes.CLASS_PARAM_CLAUSES

          def checkForParamClause[T <: Param](param : T, first : IElementType, second : IElementType, third : IElementType) : Boolean = {
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
        var chooseParsingWay = builder.mark()

        var first = builder.getTokenType()
        builder.advanceLexer
        //Console.println("first in class param clause " + first)

        var second = builder.getTokenType()
        builder.advanceLexer
        //Console.println("second in class param clause " + second)

        var third = builder.getTokenType()
        builder.advanceLexer
        //Console.println("third in class param clause " + third)

        //it is possible to cyclic
        while (checkForParamClause[T](param, first, second, third)) {
          chooseParsingWay.rollbackTo()
          new ParamClause[T](param).parse(builder)
          chooseParsingWay = builder.mark()

          first = builder.getTokenType()
          builder.advanceLexer
          second = builder.getTokenType()
          builder.advanceLexer
          third = builder.getTokenType()
          //Console.println("one param clause " + builder.getTokenType())
        }

        if (checkForImplicit(first, second, third)) {
          //Console.println("one implicit end " + builder.getTokenType())
          chooseParsingWay.rollbackTo()
          //Console.println("check for implicit")
          new ImplicitEnd[T](param).parse(builder)
        }

        chooseParsingWay.rollbackTo()
      }
    }

 class ParamClause[T <: Param] (param : T) extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.FUN_PARAM_CLAUSE

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
        ParserUtils.listOfSmthWithoutNode(builder, param, ScalaTokenTypes.tCOMMA)
      }

      if (ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
      } else {
        builder error "expected ')'"
        return
      }
    }
  }

   class ImplicitEnd[T <: Param] (param : T){
      def parse(builder : PsiBuilder) : Unit = {
         if (builder.getTokenType().equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
        }

        if (builder.getTokenType().equals(ScalaTokenTypes.tLPARENTHIS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
        } else builder.error("expected '('")

        if (builder.getTokenType().equals(ScalaTokenTypes.kIMPLICIT)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kIMPLICIT)
        } else builder.error("expected 'implicit'")

        if (param.first.contains(builder.getTokenType())) {
           param parse builder
        } else builder.error("expected parameter or list of parameters")

        if (builder.getTokenType().equals(ScalaTokenTypes.tRPARENTHIS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
        } else builder.error("expected ')'")
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

  object ParamType extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.PARAM_TYPE

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
      }

      if (BNF.firstType.contains(builder.getTokenType)) {
         Type parse builder
      } else {
        builder error "expected type declaration"
        return
      }

      if (ScalaTokenTypes.tSTAR.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.tSTAR)
      }
    }
  }

}
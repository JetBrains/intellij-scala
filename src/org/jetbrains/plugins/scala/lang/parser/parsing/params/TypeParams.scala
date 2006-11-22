package org.jetbrains.plugins.scala.lang.parser.parsing.top.params {

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 20:26:46
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


  /******************** type parameters **********************/

    class TypeParamClause[T <: TypeParam] (typeParam : T) extends Constr {
      override def getElementType : IElementType = ScalaElementTypes.TYPE_PARAM_CLAUSE

        def checkForTypeParamClause (first : IElementType, second : IElementType) : Boolean = {
          var a = first
          var b = second

          if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
            a = b
          }

          a.equals(ScalaTokenTypes.tLSQBRACKET)
        }

        /*def checkForParamClauses(first : IElementType, second : IElementType) : Boolean = {
          var a = first
          var b = second

          if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
            a = b
          }

          if (a.equals(ScalaTokenTypes.tLPARENTHIS)) return true
          else false
        } */


      override def parseBody(builder : PsiBuilder) : Unit = {
        val typeParamClausemarker = builder.mark

        val first = builder.getTokenType
        builder.advanceLexer

        val second = builder.getTokenType
        typeParamClausemarker.rollbackTo

        if (checkForTypeParamClause(first, second)) {

          if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
          }

          if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)

            if (typeParam.first.contains(builder.getTokenType)){
              ParserUtils.listOfSmth(builder, typeParam, ScalaTokenTypes.tCOMMA, ScalaElementTypes.TYPE_PARAMS)
            } else {
              builder error "expected type parameter declaration"
            }

            if (ScalaTokenTypes.tRSQBRACKET.equals(builder.getTokenType)) {
              ParserUtils.eatElement(builder, ScalaTokenTypes.tRSQBRACKET)
            } else {
              builder error "expected ']'"
              return
            }
          } else {
            builder error "expected '['"
            return
          }

        }
      }
    }

    class VariantTypeParam extends TypeParam {
      override def getElementType = ScalaElementTypes.VARIANT_TYPE_PARAM

      override def first = BNF.firstVariantTypeParam

      override def parseBody(builder : PsiBuilder) : Unit = {
        if (!first.contains(builder.getTokenType)) {
          builder.error("expected '+', '-' or identifier")
          return
        }

        if (ScalaTokenTypes.tPLUS.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tPLUS)
        }

        if (ScalaTokenTypes.tMINUS.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tMINUS)
        }

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          (new TypeParam()).parse(builder)
        }
      }
    }

    class TypeParam extends ConstrItem {
      override def getElementType = ScalaElementTypes.TYPE_PARAM

      override def first = BNF.firstTypeParam

      override def parseBody(builder : PsiBuilder) : Unit = {
        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        } else builder.error("expected identifier")

        if (ScalaTokenTypes.tLOWER_BOUND.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLOWER_BOUND)
          Type.parse(builder)
        }

        if (ScalaTokenTypes.tUPPER_BOUND.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tUPPER_BOUND)
          Type.parse(builder)
        }

        if (ScalaTokenTypes.tVIEW.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tVIEW)
          Type.parse(builder)
        }
      }
    }

}
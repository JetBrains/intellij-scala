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

/*
 *  TypeParamClause ::= [NewLine] ‘[’ VariantTypeParam { ‘,’ VariantTypeParam } ‘]’
 */

    class TypeParamClause[T <: TypeParam] (typeParam : T) extends Constr {
      override def getElementType : IElementType = ScalaElementTypes.TYPE_PARAM_CLAUSE

        def checkForTypeParamClause (first : IElementType, second : IElementType) : Boolean = {
          if (first == null || second == null) return false

          var a = first
          var b = second

          if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
            a = b
          }

          a.equals(ScalaTokenTypes.tLSQBRACKET)
        }


      override def parseBody(builder : PsiBuilder) : Unit = {
        val typeParamClauseMarker = builder.mark
        val first = builder.getTokenType
        builder.advanceLexer

        val second = builder.getTokenType
        typeParamClauseMarker.rollbackTo

        if (checkForTypeParamClause(first, second)) {

          if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
          }

          if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)
          } else {
            builder error "expected '['"
            return
          }

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

        }
      }
    }

/*
 *  VariantTypeParam::= [‘+’ | ‘’] TypeParam
 */

    class VariantTypeParam extends TypeParam {
      override def first = BNF.firstVariantTypeParam

      override def parse (builder : PsiBuilder) : Unit = {
        var variantTypeParamMarker = builder.mark

        if (!first.contains(builder.getTokenType)) {
          builder.error("expected '+', '-' or identifier")
          return
        }

        var isVariantTypeParam = false;

        //if (!isVariantTypeParam && ScalaTokenTypes.tPLUS.equals(builder.getTokenType)) {
        if (!isVariantTypeParam && "+".equals(builder.getTokenText)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tPLUS)
          isVariantTypeParam = true
        }

        //if (!isVariantTypeParam && ScalaTokenTypes.tMINUS.equals(builder.getTokenType)) {
        if (!isVariantTypeParam && "-".equals(builder.getTokenText)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tMINUS)
          isVariantTypeParam = true
        }

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          (new TypeParam()).parse(builder)
        }

        if (isVariantTypeParam) variantTypeParamMarker.done(ScalaElementTypes.VARIANT_TYPE_PARAM)
        else variantTypeParamMarker.drop
      }
    }

/*
 *  TypeParam ::= id [>: Type] [<: Type] [<% Type]
 */

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
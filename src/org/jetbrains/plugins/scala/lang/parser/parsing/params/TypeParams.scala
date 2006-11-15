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

      override def parseBody(builder : PsiBuilder) : Unit = {
         if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
           ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
        }

        if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)

          if (typeParam.first.contains(builder.getTokenType)){
            ParserUtils.listOfSmthWithoutNode(builder, typeParam, ScalaTokenTypes.tCOMMA)
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

    class VariantTypeParam extends TypeParam {
      override def getElementType = ScalaElementTypes.VARIANT_TYPE_PARAM

      override def first = BNF.firstVariantTypeParam

      override def parseBody(builder : PsiBuilder) : Unit = {
        if (!first.contains(builder.getTokenType)) {
          builder.error("expected '+', '-' or identifier")
          return
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tPLUS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tPLUS)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tMINUS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tMINUS)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
          (new TypeParam()).parse(builder)
        }
      }
    }

    class TypeParam extends ConstrItem {
      override def getElementType = ScalaElementTypes.TYPE_PARAM

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

}
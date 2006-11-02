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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.definition.FunSig

 object TypeParam extends ConstrItem {
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

 object ParamClause extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.PARAM_CLAUSE

    override def parseBody(builder : PsiBuilder) : Unit = {
       if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
      }

      if (ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)

        if (BNF.firstParam.contains(builder.getTokenType)){
          ParserUtils.listOfSmth(builder, Param, ScalaTokenTypes.tCOMMA, ScalaElementTypes.PARAM_LIST)
        }

        if (ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)) {
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

  object Param extends ConstrItem {
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
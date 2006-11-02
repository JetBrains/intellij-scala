package org.jetbrains.plugins.scala.lang.parser.parsing.top.declaration {

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.11.2006
 * Time: 15:46:49
 */

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.top.definition.FunSig
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param

    object Dcl extends Constr {
      override def getElementType : IElementType = ScalaElementTypes.DECLARATION

      override def parseBody(builder : PsiBuilder) : Unit = {
        def wrapDcl (declaration: IElementType, keyword : IElementType, dclConstr : Constr) : Unit = {
          val dclMarker = builder.mark()

          ParserUtils.eatElement(builder, keyword)
          dclConstr parse builder

          dclMarker.done(declaration)
        }

        builder.getTokenType match {
          case ScalaTokenTypes.kVAL => {
            wrapDcl(ScalaElementTypes.VALUE_DECLARATION, ScalaTokenTypes.kVAL, ValDcl)
          }

          case ScalaTokenTypes.kVAR => {
            wrapDcl(ScalaElementTypes.VARIABLE_DECLARATION, ScalaTokenTypes.kVAR, VarDcl)
          }

          case ScalaTokenTypes.kDEF => {
            wrapDcl(ScalaElementTypes.FUNCTION_DECLARATION, ScalaTokenTypes.kDEF, FunDcl)
          }

          case ScalaTokenTypes.kTYPE => {
            wrapDcl(ScalaElementTypes.TYPE_DECLARATION, ScalaTokenTypes.kTYPE, TypeDcl)
          }

          case _ => builder error "wrong declaration"
        }
      }
    }

     abstract class ValVarDcl extends Constr{
        override def parseBody(builder : PsiBuilder) : Unit = {
          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
            Ids parse builder
          } else {
            builder error "expected idnetifier"
            return
          }

          if (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
          } else {
            builder error "expected ','"
            return
          }

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "expected type declaration"
            return
          }
        }
      }

      object VarDcl extends ValVarDcl {
        override def getElementType : IElementType = ScalaElementTypes.VAR_DCL
      }

      object ValDcl extends ValVarDcl {
        override def getElementType : IElementType = ScalaElementTypes.VAL_DCL
      }

      object FunDcl extends Constr {
        override def getElementType : IElementType = ScalaElementTypes.FUN_DCL

        override def parseBody(builder : PsiBuilder) : Unit = {
          if (BNF.firstFunSig.contains(builder.getTokenType)) {
            FunSig parse builder
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

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "wrong type declaration"
            return
          }
        }
      }

      object TypeDcl extends Constr {
        override def getElementType : IElementType = ScalaElementTypes.TYPE_DCL

        override def parseBody(builder : PsiBuilder) : Unit = {
          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          } else {
            builder error "expected identifier"
            return
          }

          if (ScalaTokenTypes.tLOWER_BOUND.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLOWER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
              return
            }
          }

          if (ScalaTokenTypes.tUPPER_BOUND.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tUPPER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
              return
            }
          }
        }
      }

}
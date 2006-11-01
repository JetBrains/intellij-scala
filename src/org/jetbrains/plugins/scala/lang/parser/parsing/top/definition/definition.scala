package org.jetbrains.plugins.scala.lang.parser.parsing.top.definition {

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.Constr
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.top.FunSig

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.10.2006
 * Time: 19:45:50
 */

    object Def extends ConstrList {
      override def getElementType : IElementType = ScalaElementTypes.DEFINITION

      override def first : TokenSet = TokenSet.orSet(
        Array (
          TmplDef.first,
          TokenSet.create(
            Array (
              ScalaTokenTypes.kVAR,
              ScalaTokenTypes.kVAL,
              ScalaTokenTypes.kDEF,
              ScalaTokenTypes.kTYPE
            )
          )
        )
      )

      override def parseBody(builder : PsiBuilder) : Unit = {

        def wrapDef (definition : IElementType, keyword : IElementType, defConstr : Constr) : Unit = {
          val defMarker = builder.mark()

          ParserUtils.eatElement(builder, keyword)
          defConstr parse builder

          defMarker.done(definition)
        }

        builder.getTokenType match {
          case ScalaTokenTypes.kVAL => {
            wrapDef(ScalaElementTypes.VALUE_DEFINITION, ScalaTokenTypes.kVAL, PatDef)
          }

          case ScalaTokenTypes.kVAR => {
            wrapDef(ScalaElementTypes.VARIABLE_DEFINITION, ScalaTokenTypes.kVAR, VarDef)
          }

          case ScalaTokenTypes.kDEF => {
            wrapDef(ScalaElementTypes.FUNCTION_DEFINITION, ScalaTokenTypes.kDEF, FunDef)
          }

          case ScalaTokenTypes.kTYPE => {
            wrapDef(ScalaElementTypes.TYPE_DEFINITION, ScalaTokenTypes.kTYPE, TypeDef)
          }

          case _ => {
            if (BNF.firstTmplDef.contains(builder.getTokenType)) {
              TmplDef parse builder
            } else {
              builder error "wrong declaration"
              return
            }
          }
        }
      }
    }


   object PatDef extends ConstrList {
      override def getElementType : IElementType = ScalaElementTypes.PAT_DEF

      override def first : TokenSet = TokenSet.create(
        Array (
          ScalaTokenTypes.tIDENTIFIER
        )
      )


      //todo: change iddentifier to Pattern2
      override def parseBody(builder : PsiBuilder) : Unit = {
        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType) && Character.isLowerCase(builder.getTokenText.charAt(0))) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        } else {
          builder error "expected identifier started on lower case letter"
          return
        }

        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type.parse(builder)
          } else {
            builder error "expected type declaration"
            return
          }
        }

        if (ScalaTokenTypes.tEQUAL.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tEQUAL)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr.parse(builder)
          } else {
            builder error "expected expression"
            return
          }
        }
    }
  }

   object VarDef extends ConstrList {
        override def getElementType : IElementType = ScalaElementTypes.VAR_DEF

        override def first : TokenSet = TokenSet.create(
          Array (
            ScalaTokenTypes.tIDENTIFIER
          )
        )

        override def parseBody(builder : PsiBuilder) : Unit = {
          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
            Ids parse builder
          } else {
            builder error "expected identifier"
            return
          }

          if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type.parse(builder)
            } else {
              builder error "expected type declaration"
              return
            }
          }

          if (ScalaTokenTypes.tEQUAL.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tEQUAL)

            if (BNF.firstExpr.contains(builder.getTokenType)) {
              Expr.parse(builder)
            } else {
              builder error "expected expression"
              return
            }
          }
      }
    }

//todo: add 'this' case
  object FunDef extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.FUN_DEF

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (BNF.firstFunSig.contains(builder.getTokenType)) {
        FunSig parse builder

        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder

            if (ScalaTokenTypes.tEQUAL.equals(builder.getTokenType)){
              ParserUtils.eatElement(builder, ScalaTokenTypes.tEQUAL)

               if (BNF.firstExpr.contains(builder.getTokenType)){
                 Expr parse builder

               } else {
                 builder error "expected expression"
                 return
               }
            } else {
              builder error "expected '='"
              return
            }

          } else {
            builder error "expected type declaration"
            return
          }
        } else {
            builder error "expected ':'"
            return
        }
      } else {
        builder error "expected identifier"
        return
      }

    }
  }

  //todo: add [TypePapramClause]
   object TypeDef extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.FUN_DEF

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)

      } else {
        builder error "expected identifier"
        return
      }

    }
  }

}
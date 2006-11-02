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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClause

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.10.2006
 * Time: 19:45:50
 */

    object Def extends ConstrItem {
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

         Console.println("token : " + builder.getTokenType)
        builder.getTokenType match {
          case ScalaTokenTypes.kVAL => {
            Console.println("kVAL")
            wrapDef(ScalaElementTypes.VALUE_DEFINITION, ScalaTokenTypes.kVAL, PatDef)
          }

          case ScalaTokenTypes.kVAR => {
            Console.println("kVAR")
            wrapDef(ScalaElementTypes.VARIABLE_DEFINITION, ScalaTokenTypes.kVAR, VarDef)
          }

          case ScalaTokenTypes.kDEF => {
          Console.println("kDEF")
            wrapDef(ScalaElementTypes.FUNCTION_DEFINITION, ScalaTokenTypes.kDEF, FunDef)
          }

          case ScalaTokenTypes.kTYPE => {
          Console.println("kTYPE")
            wrapDef(ScalaElementTypes.TYPE_DEFINITION, ScalaTokenTypes.kTYPE, TypeDef)
          }

          case _ => {
            Console.println("other" + builder.getTokenType)
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


   object PatDef extends ConstrItem {
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

        if (ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr.parse(builder)
          } else {
            builder error "expected expression"
            return
          }
        }
    }
  }

   object VarDef extends ConstrItem {
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

          if (ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

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
      Console.println("fun sig expected : "+ builder.getTokenType )
      if (BNF.firstFunSig.contains(builder.getTokenType)) {
        FunSig parse builder

        Console.println("colon expected : "+ builder.getTokenType )
        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          Console.println("Type expected : "+ builder.getTokenType )
          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder

            Console.println("eq expected : "+ builder.getTokenType )
            Console.println("is eq : " + ScalaTokenTypes.tASSIGN.equals(builder.getTokenType))
            if (ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)){
              ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

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

   object FunTypeParamClause extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.FUN_TYPE_PARAM_CLAUSE

    override def parseBody(builder : PsiBuilder) : Unit = {
       if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
      }

      if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)

        if (BNF.firstTypeParam.contains(builder.getTokenType)){
          ParserUtils.listOfSmth(builder, TypeParam, ScalaTokenTypes.tCOMMA, ScalaElementTypes.TYPE_PARAM_LIST)
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

  object FunSig extends Constr {
      override def getElementType : IElementType = ScalaElementTypes.FUN_SIG

      override def parseBody(builder : PsiBuilder) : Unit = {
         if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)

          if (BNF.firstTypeParam.contains(builder.getTokenType)) {
            FunTypeParamClause parse builder
          }

          while (BNF.firstParamClause.contains(builder.getTokenType)) {
            ParamClause parse builder
          }

        } else {
          builder error "expected identifier"
          return
        }

      }
    }


}
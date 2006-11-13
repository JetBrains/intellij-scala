package org.jetbrains.plugins.scala.lang.parser.parsing.top.template {

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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.ConstrUnpredict

    object DclDef extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        builder.getTokenType match {
          case ScalaTokenTypes.kVAL => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kVAL)
            //wrapDcl(ScalaElementTypes.VALUE_DECLARATION, ScalaTokenTypes.kVAL, ValDcl)
            //Console.println("kVAL")
            Val parse builder
          }

          case ScalaTokenTypes.kVAR => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kVAR)
          //Console.println("kVAR")
            //wrapDcl(ScalaElementTypes.VARIABLE_DECLARATION, ScalaTokenTypes.kVAR, VarDcl)
            Var parse builder
          }

          case ScalaTokenTypes.kDEF => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kDEF)
          //Console.println("kDEF")
            //wrapDcl(ScalaElementTypes.FUNCTION_DECLARATION, ScalaTokenTypes.kDEF, FunDcl)
            Fun parse builder
          }

          case ScalaTokenTypes.kTYPE => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kTYPE)
          //Console.println("kTYPE")
            //wrapDcl(ScalaElementTypes.TYPE_DECLARATION, ScalaTokenTypes.kTYPE, TypeDcl)
            TypeDclDef parse builder
          }

          case _
          => {
            if (BNF.firstTmplDef.contains(builder.getTokenType)) {
              //Console.println("TMPL")
              TmplDef parse builder
            } else builder error "wrong declaration"
          }

        }
      }
    }

  //todo: check for return Definition
    object Def extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        if (BNF.firstDef.contains(builder.getTokenType)){
          DclDef parse builder
        }
      }
    }

    object Var extends ConstrUnpredict {

      override def parseBody(builder : PsiBuilder) : Unit = {
      //Console.println("val parse")
        val varMarker = builder.mark()

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          Ids parse builder
        } else {
          builder error "expected identifier"
          varMarker.drop()
          return
        }

        var hasTypeDcl = false
        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "expected type declaration"
            varMarker.drop()
            return
          }

          hasTypeDcl = true
        }

        //if there is no '=' it is mean, that construction is declaration, else definition
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong variable declaration"
          }

          varMarker.done(ScalaElementTypes.VARIABLE_DECLARATION)
          return
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else if (ScalaTokenTypes.tUNDER.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.tUNDER)
          } else {
            builder error "wrong start of expression"
            varMarker.drop()
            return
          }

          varMarker.done(ScalaElementTypes.VARIABLE_DEFINITION)
          return
        }

        varMarker.drop()
      }
    }

    object Val extends ConstrUnpredict {

        //change ids to pattern2
      override def parseBody(builder : PsiBuilder) : Unit = {
        val valMarker = builder.mark()

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          Ids parse builder
        } else {
          builder error "expected idnetifier"
          valMarker.drop()
          return
        }

        var hasTypeDcl = false

        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "expected type declaration"
            valMarker.drop()
            return
          }

          hasTypeDcl = true
        }

        //if there is no '=' it is mean, that construction is declaration, else definition
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong variable declaration"
            valMarker.drop()
            return
          }

          valMarker.done(ScalaElementTypes.VALUE_DECLARATION)
          return
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else {
            builder error "wrong start of expression"
            valMarker.drop()
            return
          }

          valMarker.done(ScalaElementTypes.PATTERN_DEFINITION)
          return
        }

        valMarker.drop()
      }
    }

      object Fun extends ConstrUnpredict {
        override def parseBody(builder : PsiBuilder) : Unit = {
        //Console.println("fun parse")
          val defMarker = builder.mark()

         //todo: add parsing constructor

         /* if (ScalaTokenTypes.kTHIS.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kTHIS)

            defMarker.done(ScalaElementTypes.FUNCTION_DEFINITION)
            return
          }*/

          if (BNF.firstFunSig.contains(builder.getTokenType)) {
            //Console.println("funSig parse")
            FunSig parse builder
            //Console.println("funSig parsed")
          } else {
            builder error "expected identifier"
            defMarker.drop()
            return
          }

          var hasTypeDcl = false
          if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

            if (BNF.firstType.contains(builder.getTokenType)) {
            //Console.println("type parse")
              Type parse builder
              //Console.println("type parsed")
            } else {
              builder error "wrong type declaration"
              defMarker.drop()
              return
            }
            hasTypeDcl = true

          }

        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong function declaration"
            defMarker.drop()
            return
          }

          defMarker.done(ScalaElementTypes.FUNCTION_DECLARATION)
          return
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
          //Console.println("expr in fun parse")
            Expr parse builder
            //Console.println("expr in fun parsed")
          } else {
            builder error "wrong start of expression"
            defMarker.drop()
            return
          }

          //Console.println("fun parsed")
          defMarker.done(ScalaElementTypes.FUNCTION_DEFINITION)
          return
        }
        defMarker.drop()

        }
      }

      object TypeDclDef extends ConstrUnpredict {
        override def parseBody(builder : PsiBuilder) : Unit = {
          //Console.println("type parse")
          val typeMarker = builder.mark()

          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          } else {
            builder error "expected identifier"
            typeMarker.drop()
            return
          }

          if (ScalaTokenTypes.tLOWER_BOUND.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLOWER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
              typeMarker.drop()
              return
            }
          }

          if (ScalaTokenTypes.tUPPER_BOUND.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tUPPER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
              typeMarker.drop()
              return
            }
          }

          var isView = false
          if (ScalaTokenTypes.tVIEW.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.tVIEW)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
              typeMarker.drop()
              return
            }

            isView = true
          }

          if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)){
            if (isView){
              builder error "wrong type definition"
              typeMarker.drop()
              return
            }
            typeMarker.done(ScalaElementTypes.TYPE_DECLARATION)
          } else {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
              typeMarker.drop()
              return
            }

            //Console.println("type parsed")
            typeMarker.done(ScalaElementTypes.TYPE_DEFINITION)
            return
          }
          typeMarker.drop()
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
          ParserUtils.listOfSmthWithoutNode(builder, TypeParam, ScalaTokenTypes.tCOMMA)
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

          if (BNF.firstFunTypeParam.contains(builder.getTokenType)) {
            FunTypeParamClause parse builder
          }

          if (BNF.firstParamClause.contains(builder.getTokenType)) {
            //Console.println("ParamClauses parsing " + builder.getTokenType)
            (new ParamClauses[Param](new Param)).parse(builder)
            //Console.println("ParamClauses parsed " + builder.getTokenType)
          }

        } else {
          builder error "expected identifier"
          return
        }

      }
    }
}
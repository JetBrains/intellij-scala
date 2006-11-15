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
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.ConstrUnpredict

    object DclDef extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        parseBodyNode(builder)
      }

      def parseBodyNode(builder : PsiBuilder) : IElementType = {
        builder.getTokenType match {
          case ScalaTokenTypes.kVAL => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kVAL)
            return (Val parseBodyNode builder)
          }

          case ScalaTokenTypes.kVAR => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kVAR)
            return (Var parseBodyNode builder)
          }

          case ScalaTokenTypes.kDEF => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kDEF)
            return (Fun parseBodyNode builder)
          }

          case ScalaTokenTypes.kTYPE => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kTYPE)
            return (TypeDclDef parseBodyNode builder)
          }

          case _ => {
            if (BNF.firstTmplDef.contains(builder.getTokenType)) {
              //Console.println("TMPL")
              return (TmplDef parseBodyNode builder)
            } else {
              builder error "wrong declaration"
              return ScalaElementTypes.WRONGWAY
            }
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
        parseBodyNode(builder)
      }

      def parseBodyNode(builder : PsiBuilder) : IElementType = {
      //Console.println("val parse")
       // val varMarker = builder.mark()

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          Ids parse builder
        } else {
          builder error "expected identifier"
         // varMarker.drop()
          return ScalaElementTypes.WRONGWAY
        }

        var hasTypeDcl = false
        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "expected type declaration"
           // varMarker.drop()
            return ScalaElementTypes.WRONGWAY
          }

          hasTypeDcl = true
        }

        //if there is no '=' it is mean, that construction is declaration, else definition
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong variable declaration"
          }

          //varMarker.done(ScalaElementTypes.VARIABLE_DECLARATION)
          return ScalaElementTypes.VARIABLE_DECLARATION
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else if (ScalaTokenTypes.tUNDER.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.tUNDER)
          } else {
            builder error "wrong start of expression"
            //varMarker.drop()
            return ScalaElementTypes.WRONGWAY
          }

          //varMarker.done(ScalaElementTypes.VARIABLE_DEFINITION)
          return ScalaElementTypes.VARIABLE_DEFINITION
        }

        //varMarker.drop()
        return ScalaElementTypes.WRONGWAY
      }
    }

    object Val extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        parseBodyNode(builder)
      }
        //change ids to pattern2
      def parseBodyNode(builder : PsiBuilder) : IElementType = {
        //val valMarker = builder.mark()

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          Ids parse builder
        } else {
          builder error "expected idnetifier"
//          valMarker.drop()
          return ScalaElementTypes.WRONGWAY
        }

        var hasTypeDcl = false

        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "expected type declaration"
//            valMarker.drop()
            return ScalaElementTypes.WRONGWAY
          }

          hasTypeDcl = true
        }

        //if there is no '=' it is mean, that construction is declaration, else definition
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong variable declaration"
//            valMarker.drop()
            return ScalaElementTypes.WRONGWAY
          }

//          valMarker.done(ScalaElementTypes.VALUE_DECLARATION)
          return ScalaElementTypes.VALUE_DECLARATION
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else {
            builder error "wrong start of expression"
//            valMarker.drop()
            return ScalaElementTypes.WRONGWAY
          }

//          valMarker.done(ScalaElementTypes.PATTERN_DEFINITION)
          return ScalaElementTypes.PATTERN_DEFINITION
        }

//        valMarker.drop()
        return ScalaElementTypes.WRONGWAY
      }
    }

      object Fun extends ConstrUnpredict {
        override def parseBody(builder : PsiBuilder) : Unit = {
          parseBodyNode(builder)
        }

        def parseBodyNode(builder : PsiBuilder) : IElementType = {
          //val defMarker = builder.mark()
          //val defMarker = builder.mark()

         //todo: add parsing constructor

         /* if (ScalaTokenTypes.kTHIS.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kTHIS)

            defMarker.done(ScalaElementTypes.FUNCTION_DEFINITION)
            return
          }*/

          if (BNF.firstFunSig.contains(builder.getTokenType)) {
            FunSig parse builder
          } else {
            builder error "expected identifier"
//            defMarker.drop()
            return ScalaElementTypes.WRONGWAY
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
//              defMarker.drop()
              return ScalaElementTypes.WRONGWAY
            }
            hasTypeDcl = true

          }

        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong function declaration"
//            defMarker.drop()
            return ScalaElementTypes.WRONGWAY
          }

//          defMarker.done(ScalaElementTypes.FUNCTION_DECLARATION)
          return ScalaElementTypes.FUNCTION_DECLARATION
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)
          DebugPrint println ("parsing expression after '=' : " + builder.getTokenType)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else {
            builder error "wrong start of expression"
//            defMarker.drop()
            return ScalaElementTypes.WRONGWAY
          }

//          defMarker.done(ScalaElementTypes.FUNCTION_DEFINITION)
          return ScalaElementTypes.FUNCTION_DEFINITION
        }

//        defMarker.drop()
        return ScalaElementTypes.WRONGWAY
        }
      }

      object TypeDclDef extends ConstrUnpredict {
        override def parseBody(builder : PsiBuilder) : Unit = {
          parseBodyNode(builder)
        }

        def parseBodyNode(builder : PsiBuilder) : IElementType = {
          //Console.println("type parse")
//          val typeMarker = builder.mark()

          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          } else {
            builder error "expected identifier"
//            typeMarker.drop()
            return ScalaElementTypes.WRONGWAY
          }

          if (ScalaTokenTypes.tLOWER_BOUND.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLOWER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
//              typeMarker.drop()
              return ScalaElementTypes.WRONGWAY
            }
          }

          if (ScalaTokenTypes.tUPPER_BOUND.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tUPPER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
//              typeMarker.drop()
              return ScalaElementTypes.WRONGWAY
            }
          }

          var isView = false
          if (ScalaTokenTypes.tVIEW.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.tVIEW)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
//              typeMarker.drop()
              return ScalaElementTypes.WRONGWAY
            }

            isView = true
          }

          if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)){
            if (isView){
              builder error "wrong type definition"
//              typeMarker.drop()
              return ScalaElementTypes.WRONGWAY
            }

//            typeMarker.done(ScalaElementTypes.TYPE_DECLARATION)
            return ScalaElementTypes.TYPE_DECLARATION

          } else {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
//              typeMarker.drop()
              return ScalaElementTypes.WRONGWAY
            }

//            typeMarker.done(ScalaElementTypes.TYPE_DEFINITION)
            return ScalaElementTypes.TYPE_DEFINITION
          }
//          typeMarker.drop()
          return ScalaElementTypes.WRONGWAY
        }
      }
  
    object FunSig extends Constr {
      override def getElementType : IElementType = ScalaElementTypes.FUN_SIG

      override def parseBody(builder : PsiBuilder) : Unit = {
         if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)

          if (BNF.firstFunTypeParam.contains(builder.getTokenType)) {
            //FunTypeParamClause parse builder
            new TypeParamClause[TypeParam](new TypeParam) parse builder
          }

          if (BNF.firstParamClause.contains(builder.getTokenType)) {
            //Console.println("ParamClauses parsing " + builder.getTokenType)
            (new ParamClauses[FunParam](new FunParam)).parse(builder)
            //Console.println("ParamClauses parsed " + builder.getTokenType)
          }

        } else {
          builder error "expected identifier"
          return
        }

      }
    }

    class FunParam extends Param {
      override def getElementType : IElementType = ScalaElementTypes.FUN_PARAM
    }
}
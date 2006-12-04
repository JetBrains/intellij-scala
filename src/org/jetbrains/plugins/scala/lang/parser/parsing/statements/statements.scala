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
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockStat
//import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2Item
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2
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
          val candidateOnDefElement = DclDef parseBodyNode builder

          candidateOnDefElement match {
            case  ScalaElementTypes.VARIABLE_DEFINITION
                | ScalaElementTypes.PATTERN_DEFINITION
                | ScalaElementTypes.FUNCTION_DEFINITION
                | ScalaElementTypes.TYPE_DEFINITION
                | ScalaElementTypes.OBJECT_DEF
                | ScalaElementTypes.CLASS_DEF
                | ScalaElementTypes.TRAIT_DEF
                => {}
            case _ => {
              builder error "expected definition"
              return
            }
          }
        }
      }
    }

    object Dcl extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        if (BNF.firstDcl.contains(builder.getTokenType)){
          val candidateOnDclElement = DclDef parseBodyNode builder

          candidateOnDclElement match {
            case  ScalaElementTypes.VARIABLE_DECLARATION
                | ScalaElementTypes.VALUE_DECLARATION
                | ScalaElementTypes.FUNCTION_DECLARATION
                | ScalaElementTypes.TYPE_DECLARATION
                => {}
            case _ => {
              builder error "expected declaration"
              return
            }
          }
        }
      }
    }

    object Var extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        parseBodyNode(builder)
      }

      def parseBodyNode(builder : PsiBuilder) : IElementType = {

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          Ids parse builder
        } else {
          builder error "expected identifier"
//          return ScalaElementTypes.WRONGWAY
        }

        var hasTypeDcl = false
        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "expected type declaration"
//            return ScalaElementTypes.WRONGWAY
          }

          hasTypeDcl = true
        }

        //if there is no '=' it is mean, that construction is declaration, else definition
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong variable declaration"
          }

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
//            return ScalaElementTypes.WRONGWAY
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
      def parseBodyNode(builder : PsiBuilder) : IElementType = {

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          val pattern2sMarker = builder.mark

          if (BNF.firstPattern2.contains(builder.getTokenType)){
            (new Pattern2()).parse(builder)
          } else {
            builder error "expected pattern"
            return ScalaElementTypes.WRONGWAY
          }

          var numberOfPattern2s = 1;

          while (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)

            if (BNF.firstPattern2.contains(builder.getTokenType)){
              (new Pattern2()).parse(builder)
              numberOfPattern2s = numberOfPattern2s + 1;
              
            } else {
              builder error "expected pattern"
              return ScalaElementTypes.WRONGWAY
            }
          }

          if (numberOfPattern2s > 1) pattern2sMarker.done(ScalaElementTypes.PATTERN2_LIST)
          else pattern2sMarker.drop

        } else {
          builder error "expected identifier"
//          return ScalaElementTypes.WRONGWAY
        }

        var hasTypeDcl = false

        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "expected type declaration"
//            valMarker.drop()
//            return ScalaElementTypes.WRONGWAY
          }

          hasTypeDcl = true
        }

        //if there is no '=' it is mean, that construction is declaration, else definition
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong value declaration"
//            return ScalaElementTypes.WRONGWAY
          }

          return ScalaElementTypes.VALUE_DECLARATION
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else {
            builder error "wrong start of expression"
//            return ScalaElementTypes.WRONGWAY
          }

          return ScalaElementTypes.PATTERN_DEFINITION
        }

        return ScalaElementTypes.WRONGWAY
      }
    }

      object Fun extends ConstrUnpredict {
        override def parseBody(builder : PsiBuilder) : Unit = {
          parseBodyNode(builder)
        }

        def parseBodyNode(builder : PsiBuilder) : IElementType = {
          if (BNF.firstFunSig.contains(builder.getTokenType)) {
            FunSig parse builder


          var hasTypeDcl = false
          if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
//              return ScalaElementTypes.WRONGWAY
            }
            hasTypeDcl = true

          }

        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong function declaration"
//            return ScalaElementTypes.WRONGWAY
          }

          return ScalaElementTypes.FUNCTION_DECLARATION
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)
          DebugPrint println ("parsing expression after '=' : " + builder.getTokenType)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else {
            builder error "wrong start of expression"
//            return ScalaElementTypes.WRONGWAY
          }

          return ScalaElementTypes.FUNCTION_DEFINITION
        }

        return ScalaElementTypes.WRONGWAY

      }

      if (ScalaTokenTypes.kTHIS.equals(builder.getTokenType)) {
        AdditionalConstructor parse builder
        return ScalaElementTypes.SUPPLEMENTARY_CONSTRUCTOR
      }


      builder error "expected function definition"
      return ScalaElementTypes.WRONGWAY

    }
  }

    object AdditionalConstructor extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        if (ScalaTokenTypes.kTHIS.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kTHIS)
        } else {
          builder error "expected function definition"
          return
        }

        if (BNF.firstParamClause.contains(builder.getTokenType)) {
          new ParamClause[FunParam] (new FunParam) parse builder
        } else {
          builder error "expected parameter clause declaration"
          return
        }

        if (BNF.firstParamClauses.contains(builder.getTokenType)) {
          new ParamClauses[FunParam] (new FunParam) parse builder
        }

        if (ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)
        } else {
          builder error "expected '='"
          return
        }

        if (BNF.firstConstrExpr.contains(builder.getTokenType)) {
          ConstrExpr parse builder
        } else {
          builder error "expected contructor expression"
          return
        }

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
//            return ScalaElementTypes.WRONGWAY
          }

          if (ScalaTokenTypes.tLOWER_BOUND.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLOWER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
//              typeMarker.drop()
//              return ScalaElementTypes.WRONGWAY
            }
          }

          if (ScalaTokenTypes.tUPPER_BOUND.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tUPPER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
//              typeMarker.drop()
//              return ScalaElementTypes.WRONGWAY
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
//              return ScalaElementTypes.WRONGWAY
            }

            isView = true
          }

          if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)){
            if (isView){
              builder error "wrong type definition"
//              typeMarker.drop()
//              return ScalaElementTypes.WRONGWAY
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
//              return ScalaElementTypes.WRONGWAY
            }

//            typeMarker.done(ScalaElementTypes.TYPE_DEFINITION)
            return ScalaElementTypes.TYPE_DEFINITION
          }
//          typeMarker.drop()
          return ScalaElementTypes.WRONGWAY
        }
      }

    object TypeDef extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        if (BNF.firstTypeDef.contains(builder.getTokenType)){
          val candidateOnTypeDefElement = TypeDclDef parseBodyNode builder

           candidateOnTypeDefElement match {
            case  ScalaElementTypes.TYPE_DEFINITION => {}
            case _ => {
              builder error "expected type definition"
              return
            }
          }
        }
      }
    }
  
    object FunSig extends ConstrWithoutNode {
      //override def getElementType : IElementType = ScalaElementTypes.FUN_SIG

      override def parseBody(builder : PsiBuilder) : Unit = {
         if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)

          if (BNF.firstFunTypeParam.contains(builder.getTokenType)) {
            //FunTypeParamClause parse builder
            new TypeParamClause[TypeParam](new TypeParam) parse builder
          }

          if (BNF.firstParamClauses.contains(builder.getTokenType)) {
            //Console.println("ParamClauses parsing " + builder.getTokenType)
            new ParamClauses[FunParam] (new FunParam) parse builder
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

    object ConstrExpr extends Constr {
      override def getElementType = ScalaElementTypes.CONSTR_EXPR

      override def parseBody(builder : PsiBuilder) : Unit = {
        if (BNF.firstSelfInvocation.contains(builder.getTokenType)) {
          SelfInvocation parse builder
          return
        }

        if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
        } else {
          builder error "expected '{'"
        }

          if (BNF.firstSelfInvocation.contains(builder.getTokenType)) {
            SelfInvocation parse builder
          } else {
            builder error "expected self invocation"
          }

          while (BNF.firstStatementSeparator.contains(builder.getTokenType)) {
            StatementSeparator parse builder

            if (BNF.firstBlockStat.contains(builder.getTokenType)) {
              BlockStat parse builder
            }
          }


        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
        } else {
          builder error "expected '}'"
        }
      }
    }

    object SelfInvocation extends Constr {
      override def getElementType = ScalaElementTypes.SELF_INVOCATION

      override def parseBody(builder : PsiBuilder) : Unit = {
        if (ScalaTokenTypes.kTHIS.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kTHIS)
        } else {
          builder error "expected 'this'"
          return
        }

        val argExprsMarker = builder.mark
        var numberOfArgExprs = 0;

        while (BNF.firstArgumentExprs.contains(builder.getTokenType)) {
          ArgumentExprs parse builder
          numberOfArgExprs = numberOfArgExprs + 1
        }

        if (numberOfArgExprs > 1) argExprsMarker.done(ScalaElementTypes.ARG_EXPRS_LIST)
        else argExprsMarker.drop
      }
    }

}
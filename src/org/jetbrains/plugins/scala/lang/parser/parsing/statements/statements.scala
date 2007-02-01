package org.jetbrains.plugins.scala.lang.parser.parsing.top.template {

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.11.2006
 * Time: 15:46:49
 */

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import com.intellij.psi._
import com.intellij.lang.ParserDefinition
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiManager

import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements.TemplateStatement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.VariantTypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockStat
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
              return (TmplDef parseBodyNode builder)
            } else {
              builder error "wrong declaration"
              return ScalaElementTypes.WRONGWAY
            }
          }

        }
      }

      private val DUMMY = "dummy.";
      def createTemplateStatementFromText(buffer : String, manager : PsiManager) : ASTNode = {
        def isStmt = (element : PsiElement) => (element.isInstanceOf[TemplateStatement])

        val pareserDefinition : ParserDefinition = ScalaFileType.SCALA_FILE_TYPE.getLanguage.getParserDefinition

        val text = "class a {" + buffer + "}"

        val dummyFile : PsiFile = manager.getElementFactory().createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text)

        val classDef = dummyFile.getFirstChild
        val topDefTmpl = classDef.getLastChild
        val templateBody = topDefTmpl.getFirstChild.asInstanceOf[ScalaPsiElementImpl]

        val stmt = templateBody.childSatisfyPredicateForPsiElement(isStmt)

        if (stmt == null) return null

        stmt.asInstanceOf[TemplateStatement].getNode

      }
    }

    object Def extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        if (BNF.firstDef.contains(builder.getTokenType)){
          val defMarker = builder.mark

          val candidateOnDefElement = DclDef parseBodyNode builder

          candidateOnDefElement match {
            case  ScalaElementTypes.VARIABLE_DEFINITION
                | ScalaElementTypes.PATTERN_DEFINITION
                | ScalaElementTypes.FUNCTION_DEFINITION
                | ScalaElementTypes.TYPE_DEFINITION
                | ScalaElementTypes.OBJECT_DEF
                | ScalaElementTypes.CLASS_DEF
                | ScalaElementTypes.TRAIT_DEF
                => {
                defMarker.done(candidateOnDefElement)
                }
            case _ => {
              builder error "expected definition"
              defMarker.drop
              return
            }
          }
        }
      }
    }

    object Dcl extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        if (BNF.firstDcl.contains(builder.getTokenType)){
          val dclMarker = builder.mark

          val candidateOnDclElement = DclDef parseBodyNode builder

          candidateOnDclElement match {
            case  ScalaElementTypes.VARIABLE_DECLARATION
                | ScalaElementTypes.VALUE_DECLARATION
                | ScalaElementTypes.FUNCTION_DECLARATION
                | ScalaElementTypes.TYPE_DECLARATION
                => {
                dclMarker.done(candidateOnDclElement)
                }
            case _ => {
              builder error "expected declaration"
              dclMarker.drop
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
        }

        var hasTypeDcl = false
        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "expected type declaration"
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
          }

          return ScalaElementTypes.VARIABLE_DEFINITION
        }

        return ScalaElementTypes.WRONGWAY
      }
    }

    object Val extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        parseBodyNode(builder)
      }

      def parseBodyNode(builder : PsiBuilder) : IElementType = {

        val pattern2sMarker = builder.mark

        if (BNF.firstPattern2.contains(builder.getTokenType)){
          (new Pattern2()).parse(builder)
        } else {
          builder error "expected pattern"
        }

        var numberOfPattern2s = 1;

        while (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)

          if (BNF.firstPattern2.contains(builder.getTokenType)){
            (new Pattern2()).parse(builder)
            numberOfPattern2s = numberOfPattern2s + 1;

          } else {
            builder error "expected pattern"
            pattern2sMarker.drop()
            return ScalaElementTypes.WRONGWAY
          }
        }

        if (numberOfPattern2s > 1) pattern2sMarker.done(ScalaElementTypes.PATTERN2_LIST)
        else pattern2sMarker.drop

        var hasTypeDcl = false

        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "expected type declaration"
          }

          hasTypeDcl = true
        }

        //if there is no '=' it is mean, that construction is declaration, else definition
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong value declaration"
          }

          return ScalaElementTypes.VALUE_DECLARATION
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else {
            builder error "wrong start of expression"
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
            }
            hasTypeDcl = true

          }

/*
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          if (!hasTypeDcl) {
            builder error "wrong function declaration"
          }
          return ScalaElementTypes.FUNCTION_DECLARATION
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)
          DebugPrint println ("parsing expression after '=' : " + builder.getTokenType)

          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else {
            builder error "wrong start of expression"
          }

          return ScalaElementTypes.FUNCTION_DEFINITION
        }
*/

        if (ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)
          DebugPrint println ("parsing expression after '=' : " + builder.getTokenType)
          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Expr parse builder
          } else {
            builder error "wrong start of expression"
          }
          return ScalaElementTypes.FUNCTION_DEFINITION
        } else if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)){
          Expr parse builder
          return ScalaElementTypes.FUNCTION_DECLARATION
        } else {
          /*if (!hasTypeDcl) {
            builder error "wrong function declaration"
          }*/
          return ScalaElementTypes.FUNCTION_DECLARATION
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

          if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
          }

          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          } else {
            builder error "expected identifier"
          }

          var isTypeDcl = false;
          if (ScalaTokenTypes.tLOWER_BOUND.equals(builder.getTokenType)) {
            val lowerBoundMarker = builder.mark
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLOWER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
              isTypeDcl = true
              lowerBoundMarker.done(ScalaElementTypes.LOWER_BOUND_TYPE)
            } else {
              builder error "wrong type declaration"
              lowerBoundMarker.drop()
            }
          }

          if (ScalaTokenTypes.tUPPER_BOUND.equals(builder.getTokenType)) {
            val upperBoundMarker = builder.mark
            ParserUtils.eatElement(builder, ScalaTokenTypes.tUPPER_BOUND)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
              isTypeDcl = true
              upperBoundMarker.done(ScalaElementTypes.UPPER_BOUND_TYPE)
            } else {
              builder error "wrong type declaration"
              upperBoundMarker.drop()
            }
          }

          //todo: check it
         /* var isView = false
          if (ScalaTokenTypes.tVIEW.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.tVIEW)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "wrong type declaration"
            }

            isView = true
          }*/

          var isTypeParamClause = false;
          if (BNF.firstTypeParamClause.contains(builder.getTokenType)) {
              isTypeParamClause = ScalaElementTypes.TYPE_PARAM_CLAUSE.equals(new TypeParamClause[VariantTypeParam](new VariantTypeParam) parse builder)
          }

          DebugPrint println ("isTypeParamClause " + isTypeParamClause)

          DebugPrint println ("statements - typeDef: token " + builder.getTokenType) 

          if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)){
            if (isTypeParamClause) {
              builder error "expected '='"
            }

            return ScalaElementTypes.TYPE_DECLARATION

          } else {
            if (isTypeDcl) builder error "unexpected '='"

            ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

            if (BNF.firstType.contains(builder.getTokenType)) {
              Type parse builder
            } else {
              builder error "expected type declaration"
            }

            return ScalaElementTypes.TYPE_DEFINITION
          }
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
      override def parseBody(builder : PsiBuilder) : Unit = {
         if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)

          if (BNF.firstFunTypeParam.contains(builder.getTokenType)) {
            new TypeParamClause[TypeParam](new TypeParam) parse builder
          }

          if (BNF.firstParamClauses.contains(builder.getTokenType)) {
            new ParamClauses[FunParam] (new FunParam) parse builder
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
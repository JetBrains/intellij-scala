package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Construction
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateParents
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
//import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef.ClassParam

/**
 * User: Dmitry.Krasilschikov
 * Date: 16.10.2006
 * Time: 13:54:45
 */

 /*
 TmplDef ::= [case] class ClassDef
           | [case] object ObjectDef
           | trait TraitDef

*/

object TmplDef extends ConstrWithoutNode {

   override def parseBody(builder : PsiBuilder) : Unit = {
      Console.println("token in tmplDef " + builder.getTokenType)
      val caseMarker = builder.mark()

      if (ScalaTokenTypes.kCASE.equals(builder.getTokenType)){
        builder.advanceLexer //Ate 'case'

         Console.println("token, expected class or object " + builder.getTokenType)
         builder.getTokenType match {
          case ScalaTokenTypes.kCLASS => {
            caseMarker.rollbackTo()
            ClassDef.parse(builder)
          }

          case ScalaTokenTypes.kOBJECT => {
            caseMarker.rollbackTo()
            ObjectDef.parse(builder)
          }

          case _ => {
            caseMarker.rollbackTo()
            builder error "expected object or class declaration"
          }
        }

        return
      }

      caseMarker.rollbackTo()

      Console.println("token in tmplDef for match " + builder.getTokenType)
      builder.getTokenType match {
        case ScalaTokenTypes.kCLASS => ClassDef.parse(builder)
        case ScalaTokenTypes.kOBJECT => ObjectDef.parse(builder)
        case ScalaTokenTypes.kTRAIT => TraitDef.parse(builder)
        case _ => builder error "expected class, object or trait declaration"
      }
    }

  abstract class TypeDef extends Constr

  abstract class InstanceDef extends TypeDef

  case object ObjectDef extends InstanceDef {
    def getElementType : ScalaElementType = ScalaElementTypes.OBJECT_DEF

    override def parseBody ( builder : PsiBuilder ) : Unit = {
      if (builder.getTokenType.equals(ScalaTokenTypes.kCASE)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kCASE)
      }

      if (builder.getTokenType.equals(ScalaTokenTypes.kOBJECT)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kOBJECT)
      } else {
        builder error "expected 'object'"
        return
      }

      if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      } else builder.error("expected identifier")

      if (BNF.firstClassTemplate.contains(builder.getTokenType)){
        ClassTemplate.parse(builder)
      } else builder error "object cannot have constructor"
    }
  }


   def checkForTypeParamClauses (first : IElementType, second : IElementType) : Boolean = {
        var a = first
        var b = second

        if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
          a = b
        }

        a.equals(ScalaTokenTypes.tLSQBRACKET)
      }

      def checkForClassParamClauses(first : IElementType, second : IElementType) : Boolean = {
        var a = first
        var b = second

        if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
          a = b
        }

        if (a.equals(ScalaTokenTypes.tLPARENTHIS)) true

        false
      }

    case class ClassDef extends InstanceDef {
      override def getElementType = ScalaElementTypes.CLASS_DEF

      override def parseBody ( builder : PsiBuilder ) : Unit = {

        if (builder.getTokenType.equals(ScalaTokenTypes.kCASE)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kCASE)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.kCLASS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kCLASS)
        } else {
          builder error "expected 'class'"
          return
        }
        
        Console.println("expected identifier " + builder.getTokenType)
        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)

          var chooseParsingWay = builder.mark()

          builder.advanceLexer
          val first = builder.getTokenType()
          Console.println("first " + first)

          builder.advanceLexer
          val second = builder.getTokenType()
          Console.println("second " + second)

          chooseParsingWay.rollbackTo()

          if (checkForTypeParamClauses(first, second)) {
            Console.println("checked for type parameters ")
            TypeParamClause.parse(builder)
          }

          (new ParamClauses[ClassParam](new ClassParam)).parse(builder)

          if (builder.getTokenType.equals(ScalaTokenTypes.kREQUIRES)) {
          //todo check
            SimpleType.parse(builder)
          }
          Console.println("cltemple expect " + builder.getTokenType)
          builder.getTokenType match {
            case ScalaTokenTypes.kEXTENDS
               | ScalaTokenTypes.tLINE_TERMINATOR
               | ScalaTokenTypes.tLBRACE
              => {
               ClassTemplate.parse(builder)
            }
            case _ => {}
          }
        }
      }
   }

     class ClassParam extends Param {
        override def getElementType = ScalaElementTypes.CLASS_PARAM

        override def first = BNF.firstClassParam

        override def parseBody(builder : PsiBuilder) : Unit = {

         var isModifier = false
          while (BNF.firstModifier.contains(builder.getTokenType)) {
            isModifier = true;
            builder.getTokenType() match {
              case ScalaTokenTypes.kABSTRACT => { ParserUtils.eatElement(builder, ScalaTokenTypes.kABSTRACT) }
              case ScalaTokenTypes.kFINAL => { ParserUtils.eatElement(builder, ScalaTokenTypes.kFINAL) }
              case ScalaTokenTypes.kOVERRIDE => { ParserUtils.eatElement(builder, ScalaTokenTypes.kOVERRIDE) }
              case ScalaTokenTypes.kPRIVATE => { ParserUtils.eatElement(builder, ScalaTokenTypes.kPRIVATE) }
              case ScalaTokenTypes.kPROTECTED => { ParserUtils.eatElement(builder, ScalaTokenTypes.kPROTECTED) }
              case ScalaTokenTypes.kSEALED => { ParserUtils.eatElement(builder, ScalaTokenTypes.kSEALED) }
              case _ => builder error "expected modifier"
            }
          }

         if (isModifier){
           builder.getTokenType() match {
             case ScalaTokenTypes.kVAL => { ParserUtils.eatElement(builder, ScalaTokenTypes.kVAL) }
             case ScalaTokenTypes.kVAR => { ParserUtils.eatElement(builder, ScalaTokenTypes.kVAR) }
             case _ => { builder.error("expected 'val' or 'var'") }
           }
         }

          if (builder.getTokenType().equals(ScalaTokenTypes.tIDENTIFIER)) {
            new Param().parse(builder)
          } else builder.error("expected identifier")
        }
      }

    object ClassTemplate extends Constr {
      override def getElementType = ScalaElementTypes.CLASS_TEMPLATE

      override def parseBody(builder : PsiBuilder) : Unit = {
        if (builder.getTokenType.equals(ScalaTokenTypes.kEXTENDS)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.kEXTENDS)

          if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)){
            TemplateParents.parse(builder)
          } else builder.error("expected identifier")
        }

        Console.println("before parsing templateBody " + builder.getTokenType)
        if (builder.getTokenType.equals(ScalaTokenTypes.tLBRACE)){
          TemplateBody.parse(builder)
        } else if (builder.getTokenType.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)

          if (builder.getTokenType.equals(ScalaTokenTypes.tLBRACE)){
            TemplateBody.parse(builder)
          } else builder.error("expected '{'")
        }
      }
    }


    object TypeParamClause extends Constr {
      override def getElementType = ScalaElementTypes.TYPE_PARAM_CLAUSE

      override def parseBody(builder : PsiBuilder) : Unit = {

        if (builder.getTokenType.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tLSQBRACKET)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)

          builder.getTokenType match {
            case ScalaTokenTypes.tPLUS
               | ScalaTokenTypes.tMINUS
               | ScalaTokenTypes.tIDENTIFIER
               => {
              VariantTypeParams.parse(builder)
           }

           case _ => builder error "wrong type paramters"
        }

        } else builder.error("expected '[")

        if (builder.getTokenType.equals(ScalaTokenTypes.tRSQBRACKET)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRSQBRACKET)
        } else builder.error("expected ']")
      }
    }

    object VariantTypeParams extends Constr {
      override def getElementType = ScalaElementTypes.VARIANT_TYPE_PARAMS

      override def parseBody(builder : PsiBuilder) : Unit = {
        builder.getTokenType match {
          case ScalaTokenTypes.tPLUS
             | ScalaTokenTypes.tMINUS
             | ScalaTokenTypes.tIDENTIFIER
             => {
            VariantTypeParam.parse(builder)
          }

          case _=> builder error "wrong variants type parameters"
        }

        while (builder.getTokenType.equals(ScalaTokenTypes.tCOMMA)){
          Console.println("VariantTypeParam parsing " + builder.getTokenType)
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)

          VariantTypeParam.parse(builder)
        }
      }
    }

    object VariantTypeParam extends Constr {
      override def getElementType = ScalaElementTypes.VARIANT_TYPE_PARAM

      override def parseBody(builder : PsiBuilder) : Unit = {
        if (!builder.getTokenType.equals(ScalaTokenTypes.tPLUS)
            && !builder.getTokenType.equals(ScalaTokenTypes.tMINUS)
            && !builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)){
          builder.error("expected '+', '-' or identifier")
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tPLUS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tPLUS)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tMINUS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tMINUS)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
          TypeParam.parse(builder)
        }
      }
    }


  case class TraitDef extends TypeDef {
    def getElementType = ScalaElementTypes.TRAIT_DEF

    override def parseBody ( builder : PsiBuilder ) : Unit = {

      if (ScalaTokenTypes.kTRAIT.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kTRAIT)
      } else {
        builder error "expected trait declaration"
        return
      }

      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      } else {
        builder error "expected identifier"
        return
      }

      if (BNF.firstTypeParamClause.contains(builder.getTokenType)) {
        Console.println("type param clause in trait " + builder.getTokenType);
        TypeParamClause parse builder
      }

      if (ScalaTokenTypes.kREQUIRES.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kREQUIRES)

        if (BNF.firstSimpleType.contains(builder.getTokenType)){
          SimpleType parse builder
        } else builder error "expected simple type"
      }

      if (BNF.firstTraitTemplate.contains(builder.getTokenType)){
        TraitTemplate parse builder
      } else builder error "expected trait template"
    }
  }

  object TraitTemplate extends Constr {
    override def getElementType = ScalaElementTypes.TRAIT_TEMPLATE

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (ScalaTokenTypes.kEXTENDS.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kEXTENDS)

        if (BNF.firstMixinParents.contains(builder.getTokenType)){
          MixinParents parse builder
        } else builder error "expected mixin parents"
      }

      if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)

        if (BNF.firstTemplateBody.contains(builder.getTokenType)){
          TemplateBody parse builder
        } else builder error "expected template body"
      }

      if (BNF.firstTemplateBody.contains(builder.getTokenType)){
        TemplateBody parse builder
      }
    }
  }

  object MixinParents extends Constr {
    override def getElementType = ScalaElementTypes.MIXIN_PARENTS

    override def parseBody(builder : PsiBuilder) : Unit = {

      if (BNF.firstSimpleType.contains(builder.getTokenType)){
          SimpleType parse builder
      } else builder error "expected simple type"

      if (ScalaTokenTypes.kWITH.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kWITH)

        if (BNF.firstSimpleType.contains(builder.getTokenType)){
          SimpleType parse builder
        } else builder error "expected simple type"
      }
    }
  }

}
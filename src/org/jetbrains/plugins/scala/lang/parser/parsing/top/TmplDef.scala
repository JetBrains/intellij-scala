package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateParents
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.VariantTypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.base.ModifierWithoutImplicit

/**
 * User: Dmitry.Krasilschikov
 * Date: 16.10.2006
 * Time: 13:54:45
 */


/*
 * TmplDef ::= [case] class ClassDef
 *          | [case] object ObjectDef
 *          | trait TraitDef
 *
 */

object TmplDef extends ConstrWithoutNode {
  override def parseBody(builder : PsiBuilder) : Unit = {
    val tmplDefMarker = builder.mark()
    tmplDefMarker.done(parseBodyNode(builder))
  }

   def parseBodyNode(builder : PsiBuilder) : IElementType = {
      val caseMarker = builder.mark()

      if (ScalaTokenTypes.kCASE.equals(builder.getTokenType)){
        builder.advanceLexer //Ate 'case'

         builder.getTokenType match {
          case ScalaTokenTypes.kCLASS => {
            caseMarker.rollbackTo()
            ClassDef.parse(builder)
            return ScalaElementTypes.CLASS_DEF
          }

          case ScalaTokenTypes.kOBJECT => {
            caseMarker.rollbackTo()
            ObjectDef.parse(builder)
            return ScalaElementTypes.OBJECT_DEF
          }

          case _ => {
            caseMarker.rollbackTo()
            builder error "expected object or class declaration"
            return ScalaElementTypes.WRONGWAY
          }
        }

        return ScalaElementTypes.WRONGWAY
      } else {
        caseMarker.rollbackTo()
      }

      builder.getTokenType match {
        case ScalaTokenTypes.kCLASS => {
          ClassDef.parse(builder)
          return ScalaElementTypes.CLASS_DEF
        }

        case ScalaTokenTypes.kOBJECT => {
          ObjectDef.parse(builder)
          return ScalaElementTypes.OBJECT_DEF
        }

        case ScalaTokenTypes.kTRAIT => {
          TraitDef.parse(builder)
          return ScalaElementTypes.TRAIT_DEF
        }

        case _ => {
          builder error "expected class, object or trait declaration"
          return ScalaElementTypes.WRONGWAY
        }
      }

      return ScalaElementTypes.WRONGWAY
    }

/*
 *  TypeDef presents define type structure, i.e. these are TypeDef and ClassDef
 */

  trait TypeDef extends ConstrWithoutNode

/*
 *  InstanceDef presents define instancibility of structure, i.e. these are ClassDef and ObjectDef
 */

  trait InstanceDef extends ConstrWithoutNode

  

  /************** CLASS ******************/

/*
 *  ClassDef ::= id [TypeParamClause] ClassParamClauses [‘requires’ SimpleType] ClassTemplate
 */

    case class ClassDef extends InstanceDef with TypeDef {
      //def getElementType = ScalaElementTypes.CLASS_DEF

      override def parseBody ( builder : PsiBuilder ) : Unit = {

        if (ScalaTokenTypes.kCASE.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kCASE)
        }

        if (ScalaTokenTypes.kCLASS.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kCLASS)
        } else {
          builder error "expected 'class'"
        }

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        }  else {
          builder error "expected identifier"
        }

          if (BNF.firstClassTypeParamClause.contains(builder.getTokenType)) {
            new TypeParamClause[VariantTypeParam](new VariantTypeParam) parse builder
          }

          DebugPrint println ("after ClassTypeParamClause : " + builder.getTokenType)

          if (BNF.firstClassParamClauses.contains(builder.getTokenType)) {
             (new ParamClauses[ClassParam](new ClassParam)).parse(builder)
          }

          if (ScalaTokenTypes.kREQUIRES.equals(builder.getTokenType)) {
            Requires parse builder
          }

          builder.getTokenType match {
            case ScalaTokenTypes.kEXTENDS
               | ScalaTokenTypes.tLINE_TERMINATOR
               | ScalaTokenTypes.tLBRACE
              => {
               new ClassTemplate parse builder
            }
            case _ => {}
          }

          DebugPrint println ("after classdef : " + builder.getTokenType)
      }
   }

/*
 *  Block: 'requires' with type
 */

   object Requires extends Constr {
     override def getElementType = ScalaElementTypes.REQUIRES_BLOCK

     override def parseBody(builder : PsiBuilder) : Unit = {
       if (ScalaTokenTypes.kREQUIRES.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.kREQUIRES)

          if (BNF.firstSimpleType.contains(builder.getTokenType)) {
            SimpleType parse builder
          } else {
            builder error "expected simple type declaration"
          }

       } else builder error "expected requires"
     }
   }


/*
 * ClassParam ::= [{Modifier} (‘val’ | ‘var’)] Param
 */

     class ClassParam extends Param {
        override def first = BNF.firstClassParam

        override def parse(builder : PsiBuilder) : Unit = {
          var classParamMarker = builder.mark

          var isModifier = false
          var isClassParam = false

          while (BNF.firstModifier.contains(builder.getTokenType)) {
            ModifierWithoutImplicit parse builder
            isModifier = true;
          }

           //after modifier must be 'val' or 'var'
           builder.getTokenType() match {
             case ScalaTokenTypes.kVAL => { ParserUtils.eatElement(builder, ScalaTokenTypes.kVAL); isClassParam = true }
             case ScalaTokenTypes.kVAR => { ParserUtils.eatElement(builder, ScalaTokenTypes.kVAR); isClassParam = true }

             case _ => {
               if (isModifier){
                 builder.error("expected 'val' or 'var'")
               }
                 isClassParam = false
             }
           }

          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
            new Param().parse(builder)
          } else builder.error("expected identifier")

          if (isClassParam) classParamMarker.done(ScalaElementTypes.CLASS_PARAM)
          else classParamMarker.drop
        }
      }

/*
 *  Block: 'extends' with identifier
 */

    object ExtendsBlock extends Constr {
      override def getElementType = ScalaElementTypes.EXTENDS_BLOCK

      override def parseBody(builder : PsiBuilder) : Unit = {
        if (ScalaTokenTypes.kEXTENDS.equals(builder.getTokenType)){
         ParserUtils.eatElement(builder, ScalaTokenTypes.kEXTENDS)
        } else {
          builder.error("expected 'extends'")
        }

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
          TemplateParents.parse(builder)
        } else {
          builder.error("expected identifier")
        }
      }
    }

/*
 *  ClassTemplate ::= [extends TemplateParents] [[NewLine] TemplateBody]
 */

    class ClassTemplate extends ConstrUnpredict {
      override def parseBody(builder : PsiBuilder) : Unit = {
        val classTemplateMarker = builder.mark

        if (ScalaTokenTypes.kEXTENDS.equals(builder.getTokenType)){
          ExtendsBlock parse builder
        }

        DebugPrint println ("classTemplate : " + builder.getTokenType) 
        if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)){
          TemplateBody.parse(builder)
          classTemplateMarker.done(ScalaElementTypes.CLASS_TEMPLATE)
          return
        }

        val templateBodyMarker = builder.mark

        if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)

          if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)){
            TemplateBody.parse(builder)
            templateBodyMarker.drop()
            classTemplateMarker.done(ScalaElementTypes.CLASS_TEMPLATE)
            return
          } else {
            templateBodyMarker.rollbackTo()
            classTemplateMarker.done(ScalaElementTypes.CLASS_TEMPLATE)
            return
          }
        } else {
          templateBodyMarker.rollbackTo()
        }
        
       classTemplateMarker.done(ScalaElementTypes.CLASS_TEMPLATE)
      }
    }


    /************** OBJECT ******************/

/*
 *  ObjectDef ::= id ClassTemplate
 */

   case object ObjectDef extends InstanceDef {
    def getElementType : ScalaElementType = ScalaElementTypes.OBJECT_DEF

    override def parseBody ( builder : PsiBuilder ) : Unit = {
      if (ScalaTokenTypes.kCASE.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kCASE)
      }

      if (ScalaTokenTypes.kOBJECT.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kOBJECT)
      } else {
        builder error "expected 'object'"
        return
      }

      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      } else builder.error("expected identifier")

      if (BNF.firstClassTemplate.contains(builder.getTokenType)){
        new ClassTemplate().parse(builder)
      }
    }
  }


  /************** TRAIT ******************/

/*
 *  TraitDef ::= id [TypeParamClause] [‘requires’ SimpleType] TraitTemplate
 */

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
       // return
      }

      if (BNF.firstTypeParamClause.contains(builder.getTokenType)) {
        new TypeParamClause[VariantTypeParam](new VariantTypeParam) parse builder
      }

      if (ScalaTokenTypes.kREQUIRES.equals(builder.getTokenType)) {
        Requires parse builder
      }

      if (BNF.firstTraitTemplate.contains(builder.getTokenType)){
        TraitTemplate parse builder
      } //else builder error "expected trait template"
    }
  }

/*
 *  TraitTemplate ::= [extends MixinParents] [[NewLine] TemplateBody]
 */

  object TraitTemplate extends Constr {
    override def getElementType = ScalaElementTypes.TRAIT_TEMPLATE

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (ScalaTokenTypes.kEXTENDS.equals(builder.getTokenType)) {
        var extendsMarker = builder.mark
        ParserUtils.eatElement(builder, ScalaTokenTypes.kEXTENDS)

        if (BNF.firstMixinParents.contains(builder.getTokenType)){
          MixinParents parse builder
        } else builder error "expected mixin parents"

        extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
      }

      if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)

        if (BNF.firstTemplateBody.contains(builder.getTokenType)){
          TemplateBody parse builder
        } //else builder error "expected template body"
      }

      if (BNF.firstTemplateBody.contains(builder.getTokenType)){
        TemplateBody parse builder
      }
    }
  }

/*
 *  MixinParents ::= SimpleType {‘with’ SimpleType}
 */

  object MixinParents extends Constr {
    override def getElementType = ScalaElementTypes.MIXIN_PARENTS

    override def parseBody(builder : PsiBuilder) : Unit = {

      if (BNF.firstSimpleType.contains(builder.getTokenType)){
          SimpleType parse builder
      } else builder error "expected simple type"

      while (ScalaTokenTypes.kWITH.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kWITH)

        if (BNF.firstSimpleType.contains(builder.getTokenType)){
          SimpleType parse builder
        } else builder error "expected simple type"
      }
    }
  }
}
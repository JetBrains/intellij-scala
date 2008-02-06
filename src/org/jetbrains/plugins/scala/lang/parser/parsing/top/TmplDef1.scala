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
import org.jetbrains.plugins.scala.ScalaBundle

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

/*object TmplDef extends ConstrWithoutNode {
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
            builder error "object or class declaration expected"
            return ScalaElementTypes.WRONGWAY
          }
        }

        return ScalaElementTypes.WRONGWAY
      } else {
        caseMarker.rollbackTo()
      }

      builder.getTokenType match {
        case ScalaTokenTypes.kCLASS => {
          return ClassDef.parse(builder)
        }

        case ScalaTokenTypes.kOBJECT => {
          return ObjectDef.parse(builder)
        }

        case ScalaTokenTypes.kTRAIT => {
          return TraitDef.parse(builder)
        }

        case _ => {
          builder error "class, object or trait declaration expected"
          return ScalaElementTypes.WRONGWAY
        }
      }

      return ScalaElementTypes.WRONGWAY
    } */

/*
 *  TypeDef presents define type structure, i.e. these are TypeDef and ClassDef
 */



  /************** CLASS ******************/

/*
 *  ClassDef ::= id [TypeParamClause] ClassParamClauses [requires� SimpleType] ClassTemplate
 */

    /*case class ClassDef extends InstanceDef with TypeDef {
      override def parseBody ( builder : PsiBuilder ) : IElementType = {

        if (ScalaTokenTypes.kCASE.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kCASE)
        }

        if (ScalaTokenTypes.kCLASS.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kCLASS)
        } else {    
          builder error "'class' expected"
          return ScalaElementTypes.WRONGWAY
        }

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        }  else {
          builder error "identifier expected"
          return ScalaElementTypes.WRONGWAY
        }

          if (BNF.firstClassTypeParamClause.contains(builder.getTokenType)) {
            new TypeParamClause[VariantTypeParam](new VariantTypeParam) parse builder
          }

          if (BNF.firstClassParamClauses.contains(builder.getTokenType)) {
             (new ParamClauses[ClassParam](new ClassParam)).parse(builder)
          }

          if (ScalaTokenTypes.kREQUIRES.equals(builder.getTokenType)) {
            Requires parse builder
          }
          if (!builder.eof)
          {
            ExtendsBlock.parse(builder)
          }
          else
          {
            val extendsMarker = builder.mark
            extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
          }
          if (!builder.eof)
          {
            new TypeDefTemplate[TemplateParents](new TemplateParents) parse builder
          }
          else
          {
            val templateBodyMarker = builder.mark
            templateBodyMarker.done(ScalaElementTypes.TEMPLATE_BODY)
          }

          return ScalaElementTypes.CLASS_DEF

      }
   }  */

/*
 *  Block: 'requires' with type
 */

   /*object Requires extends Constr {
     override def getElementType = ScalaElementTypes.REQUIRES_BLOCK

     override def parseBody(builder : PsiBuilder) : Unit = {
       if (ScalaTokenTypes.kREQUIRES.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.kREQUIRES)

          if (BNF.firstSimpleType.contains(builder.getTokenType)) {
            SimpleType parse builder
          } else {
            builder error "simple type declaration expected"
          }

       } else builder error "'requires' expected"
     }
   } */


/*
 * ClassParam ::= [{Modifier} (val� | var�)] Param
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
                 builder.error("'val' or 'var' expected")
               }
                 isClassParam = false
             }
           }

          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
            new Param().parse(builder)
          } else builder.error("identifier expected ")

          if (isClassParam) classParamMarker.done(ScalaElementTypes.CLASS_PARAM)
          else classParamMarker.drop
        }
      }

/*
 *  Block: 'extends' with identifier
 */

 /*   object ExtendsBlock extends ConstrReturned {
    override def parseBody(builder: PsiBuilder): IElementType = {
      val extendsMark = builder.mark
      if (!builder.eof && ScalaTokenTypes.kEXTENDS.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kEXTENDS)
        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
          new TemplateParents parse builder
          extendsMark.done(ScalaElementTypes.EXTENDS_BLOCK)
          return ScalaElementTypes.EXTENDS_BLOCK
        } else {
          extendsMark.rollbackTo
          return ScalaElementTypes.WRONGWAY
        }
      }
      extendsMark.done(ScalaElementTypes.EXTENDS_BLOCK)
      return ScalaElementTypes.EXTENDS_BLOCK
    }
  }*/

    /************** OBJECT ******************/

/*
 *  ObjectDef ::= id ClassTemplate
 */

 /*  case object ObjectDef extends InstanceDef {
    override def parseBody ( builder : PsiBuilder ) : IElementType = {
      if (ScalaTokenTypes.kCASE.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kCASE)
      }

      if (ScalaTokenTypes.kOBJECT.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kOBJECT)
      } else {
        builder error "'object' expected"
        return ScalaElementTypes.WRONGWAY
      }

      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      } else {
        builder.error("identifier expected")
        return ScalaElementTypes.WRONGWAY
      }

      if (!builder.eof)
      {
        ExtendsBlock.parse(builder)
      }
      else
      {
        val extendsMarker = builder.mark
        extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
      }

      if (!builder.eof)
      {
        new TypeDefTemplate[TemplateParents](new TemplateParents) parse builder
      }
      else
      {
        val templateBodyMarker = builder.mark
        templateBodyMarker.done(ScalaElementTypes.TEMPLATE_BODY)
      }

      return ScalaElementTypes.OBJECT_DEF
    }
  }*/


  /************** TRAIT ******************/

/*
 *  TraitDef ::= id [TypeParamClause] [requires� SimpleType] TraitTemplate
 */

 /* case class TraitDef extends TypeDef {
    override def parseBody ( builder : PsiBuilder ) : IElementType = {

      if (ScalaTokenTypes.kTRAIT.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kTRAIT)
      } else {
        builder error "trait declaration expected"
        return ScalaElementTypes.WRONGWAY
      }

      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      } else {
        builder error "identifier expected"
        return ScalaElementTypes.WRONGWAY
      }

      if (BNF.firstTypeParamClause.contains(builder.getTokenType)) {
        new TypeParamClause[VariantTypeParam](new VariantTypeParam) parse builder
      }

      if (ScalaTokenTypes.kREQUIRES.equals(builder.getTokenType)) {
        Requires parse builder
      }

      if (!builder.eof)
      {
        ExtendsBlock.parse(builder)
      }
      else
      {
        val extendsMarker = builder.mark
        extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
      }

      if (!builder.eof)
      {
        new TypeDefTemplate[MixinParents] (new MixinParents) parse builder
      }
      else
      {
        val templateBodyMarker = builder.mark
        templateBodyMarker.done(ScalaElementTypes.TEMPLATE_BODY)
      }

      return ScalaElementTypes.TRAIT_DEF
    }
  }*/


 //  Template for class and Trait
 /* class TypeDefTemplate[Parents <: TemplateParents](parents: Parents) extends ConstrUnpredict {

    override def parseBody(builder: PsiBuilder): Unit = {
      val typedefTemplateMarker = builder.mark

      val lineTerminatorMarker = builder.mark
      if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
      }

      if (BNF.firstTemplateBody.contains(builder.getTokenType)){
        lineTerminatorMarker.drop
        TemplateBody parse builder
      } else {
        lineTerminatorMarker.rollbackTo
        val templateBodyMarker = builder.mark
        templateBodyMarker.done(ScalaElementTypes.TEMPLATE_BODY)
      }
      // TODO changed 20.05.2007
      /*
            if (isRealTypedefTemplate) typedefTemplateMarker.done(ScalaElementTypes.TOP_DEF_TEMPLATE)
            else typedefTemplateMarker.drop
      */
      typedefTemplateMarker.drop
    }
  } */

/*
 *  MixinParents ::= SimpleType {with� SimpleType}
 */

 /* class MixinParents extends TemplateParents {
    override def getElementType = ScalaElementTypes.MIXIN_PARENTS

    override def first = BNF.firstMixinParents

    override def parseBody(builder : PsiBuilder) : Unit = {

      if (BNF.firstSimpleType.contains(builder.getTokenType)){
          SimpleType parse builder
      } else builder error "simple type expected"

      while (ScalaTokenTypes.kWITH.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kWITH)

        if (BNF.firstSimpleType.contains(builder.getTokenType)){
          SimpleType parse builder
        } else builder error "simple type expected"
      }
    }
  } */

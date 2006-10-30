package org.jetbrains.plugins.scala.lang.parser.parsing.top.template {

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Construction, Import, AttributeClause, Modifier, Ids}
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.10.2006
 * Time: 15:04:19
 */
object Template extends Constr{
  override def getElementType = ScalaElementTypes.TEMPLATE

  override def parseBody(builder : PsiBuilder) : Unit = {

  }
} 

  object TemplateParents extends Constr {
    override def getElementType = ScalaElementTypes.TEMPLATE_PARENTS

    override def parseBody(builder : PsiBuilder) : Unit = {


      if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
        Construction.parse(builder)
      } else builder.error("expected identifier")

      while (builder.getTokenType.equals(ScalaTokenTypes.kWITH)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kWITH)

        //todo check
        SimpleType.parse(builder)
      }
    }
  }

  object TemplateBody extends Constr {
    override def getElementType = ScalaElementTypes.TEMPLATE_BODY

    override def parseBody(builder : PsiBuilder) : Unit = {

      if (builder.getTokenType.equals(ScalaTokenTypes.tLBRACE)) {
        //ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
       //todo
        var counter = 1
        var lastRBrace = false

        ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)

     /*   if (BNF.firstTemplateStat.conatins(builder.getTokenType)) {
          TemplateStatList parse builder
        }
       */
       if (!builder.eof()) {
         builder.advanceLexer
       }

        while ( !builder.eof() && !lastRBrace){
          val token = builder.getTokenType

          if (token.equals(ScalaTokenTypes.tLBRACE)) {
          Console.println("ate '{'")
            counter = counter + 1
          }


          if (token.equals(ScalaTokenTypes.tRBRACE)) {
          Console.println("ate '}'")
            counter = counter - 1
          }

          if (counter == 0) {
            lastRBrace = true
           // builder.advanceLexer
          }

          if (!lastRBrace) {
            builder.advanceLexer
          }
        }
     /*
          if (builder.getTokenType.equals(ScalaTokenTypes.tRBRACE)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
        } else builder error "expected '}'"
       */

      }
    }

 /*   override def isParsible(CharSequence buffer, val Project project) = {

    }
   */

  }

  object TemplateStatList extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.TEMPLATE_STAT_LIST

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (BNF.firstTemplateStat.contains(builder.getTokenType)) {

        if(ScalaTokenTypes.kIMPORT.equals(builder.getTokenType)) {
         Import parse builder
         return
        }

        var isDefOrDcl = false
        while(BNF.firstAttributeClause.contains(builder.getTokenType)) {
         AttributeClause parse builder
         isDefOrDcl = true
        }

        while(BNF.firstModifier.contains(builder.getTokenType)) {
         Modifier parse builder
         isDefOrDcl = true
        }

        if (isDefOrDcl) {
          if (BNF.firstDef.contains(builder.getTokenType)) {
            Def parse builder
            return
          }

          if (BNF.firstDef.contains(builder.getTokenType)) {
            Def parse builder
            return
          }

          //error, because def or dcl must be defined after attributeClause or Modifier
          builder error "expected definition or declaration"
        }

        if (BNF.firstExpr.contains(builder.getTokenType)) {
          Expr parse builder
          return
        }

      } else builder error "wrong template declaration"

    }
  }

  object Def extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.DEFINITION

    override def parseBody(builder : PsiBuilder) : Unit = {

    }
  }

  object Dcl extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.DECLARATION

    override def parseBody(builder : PsiBuilder) : Unit = {
      builder.getTokenType match {
        case ScalaTokenTypes.kVAL => {
          ValAndVarDcl parse builder
        }

        case ScalaTokenTypes.kVAR => {
          ValAndVarDcl parse builder
        }

        case ScalaTokenTypes.kDEF => {
          FunDcl parse builder
        }

        case ScalaTokenTypes.kTYPE => {
          TypeDcl parse builder
        }

        case _ => builder error "wrong declaration"
      }
    }
  }

  object ValAndVarDcl extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.VAL_DCL

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        Ids parse builder
      } else builder error "expected idnetifier"

      if (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
      } else builder error "expected ','"

      if (BNF.firstType.contains(builder.getTokenType)) {
        Type parse builder
      } else builder error "expected type declaration"
    }
  }

  object FunDcl extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.FUN_DCL

    override def parseBody(builder : PsiBuilder) : Unit = {

    }
  }

  object TypeDcl extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.TYPE_DCL

    override def parseBody(builder : PsiBuilder) : Unit = {

    }
  }

}
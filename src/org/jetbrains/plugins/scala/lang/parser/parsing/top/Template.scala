package org.jetbrains.plugins.scala.lang.parser.parsing.top.template {

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Construction
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AttributeClause
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
//import org.jetbrains.plugins.scala.lang.parser.parsing.top.definition.Def
import org.jetbrains.plugins.scala.lang.parser.parsing.top.declaration.DclDef
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator
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
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
       //todo
        var counter = 1
        var lastRBrace = false

    //    ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)

       Console.println("Template stat seq parse " + builder.getTokenType)

        if (BNF.firstTemplateStatSeq.contains(builder.getTokenType)) {
          Console.println("parse template stat list")
          TemplateStatSeq parse builder
          Console.println("parsed template stat list")
        }
       
      /* if (!builder.eof()) {
         builder.advanceLexer
       }*/

       //in case class A {}
     /*  if (builder.getTokenType.equals(ScalaTokenTypes.tRBRACE)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
          return
        } else builder error "expected '}'"


        while ( !builder.eof() && !lastRBrace){
          builder.advanceLexer

          if (!builder.eof()) {
            val token = builder.getTokenType

            if (token.equals(ScalaTokenTypes.tLBRACE)) {
            Console.println("ate '{'" + builder.getTokenType)
              counter = counter + 1
            }


            if (token.equals(ScalaTokenTypes.tRBRACE)) {
            Console.println("ate '}'" + builder.getTokenType)
              counter = counter - 1
            }

            if (counter == 0) {
              lastRBrace = true
             // builder.advanceLexer
            }
          }
        }
        */
        if (!builder.eof() && builder.getTokenType.equals(ScalaTokenTypes.tRBRACE)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
        } else builder error "expected '}'"

        
      }
    }
  }

  object TemplateStatSeq extends Constr {
    override def getElementType : IElementType = ScalaElementTypes.TEMPLATE_STAT_LIST

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (BNF.firstTemplateStat.contains(builder.getTokenType)) {
        Console.println("single Template Stat " + builder.getTokenType)
        TemplateStat parse builder
      }

        while (!builder.eof() && BNF.firstStatementSeparator.contains(builder.getTokenType)) {
          Console.println("parse StatementSeparator " + builder.getTokenType)
          StatementSeparator parse builder

          if (BNF.firstTemplateStat.contains(builder.getTokenType)) {
            Console.println("parse TemplateStat " + builder.getTokenType)
            TemplateStat parse builder
          } 
        }
        Console.println("single Template Stat done " + builder.getTokenType)
    }
  }

  object TemplateStat extends ConstrItem {
    override def getElementType : IElementType = ScalaElementTypes.TEMPLATE_STAT

    override def first : TokenSet = BNF.firstTemplateStat

    override def parseBody(builder : PsiBuilder) : Unit = {
      //if (BNF.firstTemplateStat.contains(builder.getTokenType)) {
        Console.println("in template stat : "+ builder.getTokenType)

        if(ScalaTokenTypes.kIMPORT.equals(builder.getTokenType)) {
         Import parse builder
         return
        }

        var isDefOrDcl = false
        while(BNF.firstAttributeClause.contains(builder.getTokenType)) {
         Console.println("attribute clause invoke")
         AttributeClause parse builder
         Console.println("attribute clause invoked")
         isDefOrDcl = true
        }

        while(BNF.firstModifier.contains(builder.getTokenType)) {
         Console.println("modifier clause invoke")
         Modifier parse builder
         Console.println("modifier clause invoked")
         isDefOrDcl = true
        }

        if (isDefOrDcl) {
          if (BNF.firstDclDef.contains(builder.getTokenType)) {
              Console.println("dcldef parse" + builder.getTokenType)
              DclDef parse builder
              Console.println("dcldef parsed")
              return
            } else {
            builder error "expected definition or declaration"
            Console.println("template stat done : "+ builder.getTokenType)
            return
          }
          //error, because def or dcl must be defined after attributeClause or Modifier
        } 

        if (BNF.firstDclDef.contains(builder.getTokenType)) {
          Console.println("dcl parse" + builder.getTokenType)
          DclDef parse builder
          Console.println("dcl parsed")
          Console.println("template stat done : "+ builder.getTokenType)
          return
        }

        if (BNF.firstExpr.contains(builder.getTokenType)) {
          Expr parse builder
          Console.println("template stat done : "+ builder.getTokenType)
          return
        }
        Console.println("template stat done : "+ builder.getTokenType)
        return

     // } else builder error "wrong template declaration"

    }
  }
   

  
}
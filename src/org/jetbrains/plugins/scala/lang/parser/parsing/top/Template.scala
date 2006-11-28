package org.jetbrains.plugins.scala.lang.parser.parsing.top.template {

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AttributeClause
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifiers
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.DclDef
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.10.2006
 * Time: 15:04:19
 */
 
object Template extends Constr{
  override def getElementType = ScalaElementTypes.TEMPLATE

  override def parseBody (builder : PsiBuilder) : Unit = {
    if (BNF.firstTemplateParents.contains(builder.getTokenType)){
      TemplateParents parse builder
    } else builder error "expected template parents"

    if (BNF.firstTemplateBody.contains(builder.getTokenType)){
      TemplateBody parse builder
    }
  }
} 

  object TemplateParents extends Constr {
    override def getElementType = ScalaElementTypes.TEMPLATE_PARENTS

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        Constructor.parse(builder)
      } else builder.error("expected identifier")

      while (ScalaTokenTypes.kWITH.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kWITH)

        if (BNF.firstSimpleType.contains(builder.getTokenType)) {
          SimpleType.parse(builder)
        }
      }
    }
  }

  object TemplateBody extends Constr {
    override def getElementType = ScalaElementTypes.TEMPLATE_BODY

    override def parseBody(builder : PsiBuilder) : Unit = {

      if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)

        if (BNF.firstTemplateStatSeq.contains(builder.getTokenType)) {
          //Console.println("parse template stat list")
          TemplateStatSeq parse builder
          //Console.println("parsed template stat list")
        }

        if (!builder.eof() && ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
          return
        }

    /*    while (!builder.eof || !BNF.lastTemplateStat.equals(builder.getTokenType)) {
          builder advanceLexer
        }

        builder error "expected '}'"

        if (builder.eof) return

        if (BNF.lastTemplateStat.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
          return
        }           */
      }
    }
  }

  object TemplateStatSeq extends ConstrWithoutNode {
    override def parseBody(builder : PsiBuilder) : Unit = {

      var isError = false;
      var isEnd = false;
      while (!builder.eof && !isError && !isEnd) {

        isError = false

        while (BNF.firstStatementSeparator.contains(builder.getTokenType)) {
          StatementSeparator parse builder
          DebugPrint println ("TemplateStatSeq: StatementSeparator parse " + builder.getTokenType)
        }

        if (BNF.firstTemplateStat.contains(builder.getTokenType)) {
          TemplateStat.parse(builder)
        }

        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType) || builder.eof) {
          isEnd = true;
        }

        if (!isEnd && !BNF.firstStatementSeparator.contains(builder.getTokenType)) {
          isError = true;
          builder error "expected line teminator or '}'"
        }

        DebugPrint println ("TemplateStatSeq: token " + builder.getTokenType)

      }
  /*    if (BNF.firstTemplateStat.contains(builder.getTokenType)) {
        DebugPrint.println("single Template Stat " + builder.getTokenType)
        TemplateStat parse builder
      }

        var isError = true
        while (!builder.eof() && BNF.firstStatementSeparator.contains(builder.getTokenType)) {
          //Console.println("parse StatementSeparator " + builder.getTokenType)
          StatementSeparator parse builder

          //Console.println("candidate to TemplateStat " + builder.getTokenType)
//          if (BNF.firstTemplateStat.contains(builder.getTokenType)) {
//            TemplateStat parse builder
//          }
//            DebugPrint.println("parse TemplateStat " + builder.getTokenType)
//            if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) {
//              return
//            }

///todo StatementSeparator in stat

            if (BNF.firstTemplateStat.contains(builder.getTokenType)) {

              val errMarker = builder.mark
              TemplateStat.parse(builder)

              if (BNF.firstStatementSeparator.contains(builder.getTokenType)) {
                isError = false
                errMarker.drop()
              } else {
                isError = true
                errMarker.done(ScalaElementTypes.WRONGWAY)
              }
            }
          *//*  } else if (!BNF.firstStatementSeparator.contains(builder.getTokenType)) {
              builder error "wrong template statement delaration"
              isError = true
            } else {
              isError = false
            }
            *//*
            if (isError){
//              ParserUtils.parseTillLast(builder, TokenSet.orSet(Array(BNF.firstStatementSeparator, TokenSet.create(Array(ScalaTokenTypes.tRBRACE)))))
              ParserUtils.parseTillLast(builder, TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.tRBRACE)))))
            }

            DebugPrint println "after template stat" + builder.getTokenType
        }
        //Console.println("single Template Stat done " + builder.getTokenType)*/
    }
  }

  object TemplateStat extends ConstrUnpredict {
    //override def getElementType : IElementType = ScalaElementTypes.TEMPLATE_STAT

    //override def first : TokenSet = BNF.firstTemplateStat

    override def parseBody(builder : PsiBuilder) : Unit = {
      //if (BNF.firstTemplateStat.contains(builder.getTokenType)) {
        //Console.println("in template stat : "+ builder.getTokenType)

        DebugPrint println "template statement parsing"
        DebugPrint println ("token type : " + builder.getTokenType)
        
        if(ScalaTokenTypes.kIMPORT.equals(builder.getTokenType)) {
         Import parse builder
         return
        }

        var statementDefDclMarker = builder.mark()

        var isDefOrDcl = false
        while(BNF.firstAttributeClause.contains(builder.getTokenType)) {
         //Console.println("attribute clause invoke")
         AttributeClause parse builder
         //Console.println("attribute clause invoked")
         isDefOrDcl = true
        }

        while(BNF.firstModifier.contains(builder.getTokenType)) {
         //Console.println("modifier clause invoke")
         Modifiers parse builder
         //Console.println("modifier clause invoked")
         isDefOrDcl = true
        }

        var defOrDclElement : IElementType = ScalaElementTypes.WRONGWAY
        if (isDefOrDcl) {
          if (BNF.firstDclDef.contains(builder.getTokenType)) {
              defOrDclElement = (DclDef parseBodyNode builder)
              statementDefDclMarker.done(defOrDclElement)
            } else {
              //error, because def or dcl must be defined after attributeClause or Modifier
              builder error "expected definition or declaration"
            }

          return
        }

        if (BNF.firstDclDef.contains(builder.getTokenType)) {
          defOrDclElement = DclDef.parseBodyNode(builder)
          statementDefDclMarker.done(defOrDclElement)
          return
        }

        statementDefDclMarker.drop()

        if (BNF.firstExpr.contains(builder.getTokenType)) {
          Expr parse builder
          return
        }

        return

    }
  }

  
}
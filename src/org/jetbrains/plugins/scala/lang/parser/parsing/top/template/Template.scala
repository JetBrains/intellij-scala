package org.jetbrains.plugins.scala.lang.parser.parsing.top.template

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
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.10.2006
 * Time: 15:04:19
 */

/*
 *  Template ::= TemplateParents [TemplateBody]
 */
 
/*object Template extends Constr{
  override def getElementType = ScalaElementTypes.TEMPLATE

  override def parseBody (builder : PsiBuilder) : Unit = {
    if (BNF.firstTemplateParents.contains(builder.getTokenType)){
      new TemplateParents parse builder
    } else builder error "template parents expected"

    if (BNF.firstTemplateBody.contains(builder.getTokenType)){
      TemplateBody parse builder
    }
  }
}*/

/*
 *  TemplateParents ::= Constr {with SimpleType}
 */

 /* class TemplateParents extends ConstrItem {
    override def getElementType = ScalaElementTypes.TEMPLATE_PARENTS

    override def first = BNF.firstTemplateParents
    
    override def parseBody(builder : PsiBuilder) : Unit = {
      if (BNF.firstTemplateParents.contains(builder.getTokenType)) {
        Constructor.parse(builder)
      } else builder.error("identifier expected")

      while (ScalaTokenTypes.kWITH.equals(builder.getTokenType)) {
        builder.advanceLexer

        if (BNF.firstSimpleType.contains(builder.getTokenType)) {
          SimpleType.parse(builder)
        }
      }
    }
  }  */

/*
 *  TemplateBody ::= { TemplateStatSeq }
 */  

  object TemplateBody extends Constr {
    override def getElementType = ScalaElementTypes.TEMPLATE_BODY

    override def parseBody(builder : PsiBuilder) : Unit = {
      if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
        builder.advanceLexer
        TemplateStatSeq parse builder
        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) {
          builder.advanceLexer
        } else {
          builder error "'}' expected"
        }
      } else {
        builder error "'{' expected"
      }
    }
  }

/*
 *  TemplateStatSeq ::= [TemplateStat] {StatementSeparator [TemplateStat}]
 */

  object TemplateStatSeq extends ConstrWithoutNode {
    override def parseBody (builder: PsiBuilder): Unit = {
      while (!builder.eof) {
        while (ScalaTokenTypes.STATEMENT_SEPARATORS.contains(builder.getTokenType)) builder.advanceLexer
        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) return

        if (!TemplateStat.parseBody(builder)) {
          builder.error("Definition or declaration expected")
          builder.advanceLexer
        }
      }
    }
  }

/*
 *  TemplateStat ::= Import
 *              | {AttributeClause} {Modifier} Def
 *              | {AttributeClause} {Modifier} Dcl
 *              | Expr
 */

  object TemplateStat {
    def parseBody(builder : PsiBuilder) : Boolean = {
        if(ScalaTokenTypes.kIMPORT.equals(builder.getTokenType)) {
         Import parse builder
         return true
        }

        var statementDefDclMarker = builder.mark()

        var isDefOrDclExpected = false
        while(BNF.firstAttributeClause.contains(builder.getTokenType)) {
         AttributeClause parse builder
         isDefOrDclExpected = true
        }

        while(BNF.firstModifier.contains(builder.getTokenType)) {
         Modifiers parse builder
         isDefOrDclExpected = true
        }

        var defOrDclElement = DclDef parseBodyNode builder
        if (ScalaElementTypes.WRONGWAY.equals(defOrDclElement)) {
          statementDefDclMarker.drop

          if (isDefOrDclExpected) {
            builder error "definition or declaration expected"
            return false
          } else {
            //try expression
            val exprElementType = Expr parse builder
            return !ScalaElementTypes.WRONGWAY.equals(exprElementType)
          }
        } else {
          statementDefDclMarker.done(defOrDclElement)
          return true
        }
      }
  }
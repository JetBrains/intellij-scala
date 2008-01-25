package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AttributeClause
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.top._
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet

/**
 * User: Dmitry.Krasilschikov
 * Date: 17.10.2006
 * Time: 11:29:50
 */

/*
*   CompilationUnit is responsible of compilable file. It can be either
*   single class, object, trait or hierarchy of package in one source file
*/

/*
*   CompilationUnit   ::=   [package QualId StatementSeparator] TopStatSeq
*        TopStatSeq   ::=   TopStat {StatementSeparator TopStat}
*           TopStat   ::=   {AttributeClause} {Modifier} TmplDef
*                         | Import
*                         | Packaging
*                         |
*/


/*
 *  CompilationUnit ::= [package QualId StatementSeparator] TopStatSeq
 */


object CompilationUnit extends ConstrWithoutNode {
  override def parseBody(builder: PsiBuilder): Unit = {

    if (ScalaTokenTypes.kPACKAGE.equals(builder.getTokenType)) {
      val packChooseMarker = builder.mark()
      builder.advanceLexer //Ate package

      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        QualId parse builder
      } else {
        builder error "qualified identifier expected"
      }

      if (builder.eof) {
        packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
        return
      }

      //If semicolon => package statement
      if (ScalaTokenTypes.tSEMICOLON.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder,ScalaTokenTypes.tSEMICOLON)
        packChooseMarker.rollbackTo
        Package.parse(builder)
      }
      //If other separator => try to understand package or packaging
      else if (ScalaTokenTypes.STATEMENT_SEPARATORS.contains(builder.getTokenType)){
        val s = builder.getTokenText
        if (s.indexOf('\n',1) != -1) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
          packChooseMarker.rollbackTo
          Package.parse(builder)
        }
        else
        {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
          if (builder.eof) {
             packChooseMarker.rollbackTo
             Package.parse(builder)
             return
          }
          if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)){
             packChooseMarker.rollbackTo
          }
          else
            packChooseMarker.rollbackTo
            Package.parse(builder)
        }
      }
      //Somthing other => try to parse packaging and catch syntactical mistake
      else
      {
        packChooseMarker.rollbackTo
      }
      if (builder.eof) {
        return
      }
    }

    TopStatSeq parse builder

  }

  /*
  *  TopStatSeq ::= TopStat {StatementSeparator TopStat}
  */

  object TopStatSeq extends ConstrWithoutNode {
    override def parseBody (builder: PsiBuilder): Unit = {
      while (!builder.eof) {
        while (ScalaTokenTypes.STATEMENT_SEPARATORS.contains(builder.getTokenType)) builder.advanceLexer

        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType) || builder.eof) return
        
        if (!TopStat.parse(builder)) {
          builder error "wrong top statement declaration"
          builder.advanceLexer
        }
      }
    }
  }

  /*
  *  TopStat ::= {AttributeClause} {Modifier} TmplDef
  *            | Import
  *            | Packaging
  */

  object TopStat {

    def parse(builder: PsiBuilder): Boolean = {

      if (ScalaTokenTypes.kIMPORT.equals(builder.getTokenType)){
        Import.parse(builder)
        return true
      }

      if (ScalaTokenTypes.kPACKAGE.equals(builder.getTokenType)){
        Packaging.parse(builder)
        return true
      }

      val tmplDefMarker = builder.mark()

      val attributeClausesMarker = builder.mark()
      var isAttrClauses = false

      while (BNF.firstAttributeClause.contains(builder.getTokenType())) {
        AttributeClause parse builder
        isAttrClauses = true
      }

      if (isAttrClauses)
        attributeClausesMarker.done(ScalaElementTypes.ATTRIBUTE_CLAUSES)
      else
        attributeClausesMarker.drop

      val modifierMarker = builder.mark()
      var isModifiers = false

      while (BNF.firstModifier.contains(builder.getTokenType)) {
        Modifier.parse(builder)
        isModifiers = true
      }

      if (isModifiers)
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
      else
        modifierMarker.drop


      var isTmpl = isAttrClauses || isModifiers;

      if (isTmpl && ! (ScalaTokenTypes.kCASE.equals(builder.getTokenType) || BNF.firstTmplDef.contains(builder.getTokenType))) {
        builder.error("wrong type declaration")
        tmplDefMarker.drop()
        return false
      }

      if (BNF.firstTmplDef.contains(builder.getTokenType)) {
        val tmplParsed = TmplDef.parseBodyNode(builder)
        if (!tmplParsed.equals(ScalaElementTypes.WRONGWAY)) {
          tmplDefMarker.done(tmplParsed)
          return true
        }
      }

      tmplDefMarker.drop()
      return false
    }
  }

  /*
  *  [package QualId StatementSeparator]
  */

  object Package extends Constr {
    override def getElementType = ScalaElementTypes.PACKAGE_STMT

    override def parseBody(builder: PsiBuilder): Unit = {

      if (ScalaTokenTypes.kPACKAGE.equals(builder.getTokenType)) {
        builder.advanceLexer //Ate package
      } else {
        builder error "'package' expected"
        return
      }

      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        QualId parse builder
      }

      if (ScalaTokenTypes.STATEMENT_SEPARATORS.contains(builder.getTokenType)){
        StatementSeparator parse builder
      } else {
        builder error "statement separator expected"
        return
      }
    }
  }


  object Packaging extends Constr {
    override def getElementType = ScalaElementTypes.PACKAGING

    override def parseBody(builder: PsiBuilder): Unit = {

      if (ScalaTokenTypes.kPACKAGE.equals(builder.getTokenType)) {
        builder.advanceLexer
      } else {
        builder.error("'package' expected")
        return
      }

      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        QualId parse builder
      } else {
        builder error "qualified identifier expected"
      }

      //If two new line characters => syntactical error
      if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
        val s = builder.getTokenText
        if (s.indexOf('\n',1) == -1)
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
      }

      if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)){
        builder.advanceLexer
      } else {
        builder.error("'{' expected")
        return
      }

      TopStatSeq parse builder

      if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) {
        builder.advanceLexer
      } else {
        builder.error("'}' expected")
      }
    }
  }



  object QualId extends Constr {
    override def getElementType = ScalaElementTypes.QUAL_ID
    override def parseBody(builder: PsiBuilder): Unit = {
      Qual_Id.parse(builder)
    }
  }
}
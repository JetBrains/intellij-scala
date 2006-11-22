package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AttributeClause
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder


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
*         Packaging   ::=   package QualId ‘{’ TopStatSeq ‘}’
*/


//IChameleonElementType

object CompilationUnit extends ConstrWithoutNode {
  //override def getElementType = ScalaElementTypes.COMPILATION_UNIT
  override def parseBody (builder : PsiBuilder) : Unit = {

    DebugPrint println "first token: " + builder.getTokenType
    
    if (ScalaTokenTypes.kPACKAGE.equals(builder.getTokenType)) {
      val packChooseMarker = builder.mark()
      builder.advanceLexer //Ate package
      DebugPrint println "'package' ate"

      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        QualId parse builder
        DebugPrint println "quilId ate"
      }

      var lastTokenInPackage = builder.getTokenType
      packChooseMarker.rollbackTo

      if (builder.eof) {
        packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
        return
      }

      if (BNF.firstStatementSeparator.contains(lastTokenInPackage)){
        Package parse builder
      }
    }

    TopStatSeq parse builder

    }

  /*  def addChameleon (builder : PsiBuilder) : Unit = {
      var numberOfBraces = 1;

      var startOffset = builder.getCurrentOffset()

      var text : String
      while (!builder.eof){
        text = text + builder.getTokenText

        if (builder.getTokenType.?equals(ScalaTokenTypes.tLBRACE))
          numberOfBraces = numberOfBraces + 1

        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType))
          numberOfBraces = numberOfBraces - 1

        if (numberOfBraces == 0) return

        builder advanceLexer
      }

      var endOffset = builder.getCurrentOffset();
      val chameleon : Chameleon =
    }

   */

  object TopStatSeq extends ConstrWithoutNode {
    //override def getElementType = ScalaElementTypes.TOP_STAT_SEQ

    override def parseBody (builder: PsiBuilder): Unit = {

      if (BNF.firstTopStat.contains(builder.getTokenType)) {
        TopStat.parse(builder)
       // DebugPrint println "1. next token in topstat: " + builder.getTokenType
      }

      while (!builder.eof() && (BNF.firstStatementSeparator.contains(builder.getTokenType))){
        StatementSeparator parse builder

        if (BNF.firstTopStat.contains(builder.getTokenType)) {
          TopStat.parse(builder)
        }
        //  DebugPrint println "2. next token in topstat: " + builder.getTokenType
      }
    }
  }

  object TopStat {
    def parse(builder: PsiBuilder): Unit = {

      if (ScalaTokenTypes.kIMPORT.equals(builder.getTokenType)){
        Import.parse(builder)
        return
      }

      if (ScalaTokenTypes.kPACKAGE.equals(builder.getTokenType)){
        Packaging.parse(builder)
        return
      }

      val tmplDefMarker = builder.mark()

      val attributeClausesMarker = builder.mark()
      var isAttrClauses = false

      while (BNF.firstAttributeClause.contains(builder.getTokenType())) {
        isAttrClauses = true

        AttributeClause parse builder
      }

      if (isAttrClauses)
        attributeClausesMarker.done(ScalaElementTypes.ATTRIBUTE_CLAUSES)
      else
        attributeClausesMarker.drop

      val modifierMarker = builder.mark()
      var isModifiers = false

      while (BNF.firstModifier.contains(builder.getTokenType())) {
        //Console.println("parse modifier")
        isModifiers = true
        Modifier.parse(builder)
      }

      if (isModifiers)
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
      else
        modifierMarker.drop


      var isTmpl = isAttrClauses || isModifiers;

      if (isTmpl && !(ScalaTokenTypes.kCASE.equals(builder.getTokenType) || BNF.firstTmplDef.contains(builder.getTokenType))) {
        builder.error("wrong type declaration")
        tmplDefMarker.drop()
        return
      }

      if (ScalaTokenTypes.kCASE.equals(builder.getTokenType) || BNF.firstTmplDef.contains(builder.getTokenType)) {
        tmplDefMarker.done(TmplDef.parseBodyNode(builder))
        return
      }

      tmplDefMarker.drop()
      builder error "wrong top statement declaration"
      return
    }
  }
 

    object Package extends Constr {
      override def getElementType = ScalaElementTypes.PACKAGE_STMT

      override def parseBody(builder: PsiBuilder): Unit = {

        if (ScalaTokenTypes.kPACKAGE.equals(builder.getTokenType)) {
          builder.advanceLexer //Ate package
          DebugPrint println "'package' ate"
        } else {
          builder error "expected 'package'"
          return
        }

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          QualId parse builder
          DebugPrint println "qualId ate"
        }

        if (BNF.firstStatementSeparator.contains(builder.getTokenType)){
          StatementSeparator parse builder
        } else {
          builder error "expected statement separator"
          return
        }
      }
    }

    object Packaging extends Constr {
      override def getElementType = ScalaElementTypes.PACKAGING

      override def parseBody(builder: PsiBuilder) : Unit = {

        if (ScalaTokenTypes.kPACKAGE.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kPACKAGE)
        } else {
          builder.error("expected 'package'")
          return
        }

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          QualId parse builder
          DebugPrint println "quilId ate"
        }

        var packageBlockMarker = builder.mark
        if ( ScalaTokenTypes.tLBRACE.equals(builder.getTokenType) ){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
        } else {
          builder.error("expected '{'")
          packageBlockMarker.drop
          return
        }

        TopStatSeq parse builder

        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
          packageBlockMarker.done(ScalaElementTypes.PACKAGING_BLOCK)

        } else {
          builder.error("expected '}'")
          packageBlockMarker.drop
          return
        }
      }
    }

    object QualId extends Constr {
      override def getElementType = ScalaElementTypes.QUAL_ID
      override def parseBody(builder : PsiBuilder) : Unit = {
       //todo: change to simple qualID
       StableId.parse(builder)
      }
    }
}
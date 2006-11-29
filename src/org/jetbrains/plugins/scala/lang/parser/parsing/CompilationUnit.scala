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
*         Packaging   ::=   package QualId ‘{’ TopStatSeq ‘}’
*/


/*
 *  CompilationUnit ::= [package QualId StatementSeparator] TopStatSeq
 */

object CompilationUnit extends ConstrWithoutNode {
  override def parseBody (builder : PsiBuilder) : Unit = {

    DebugPrint println "first token: " + builder.getTokenType
    
    if (ScalaTokenTypes.kPACKAGE.equals(builder.getTokenType)) {
      val packChooseMarker = builder.mark()
      builder.advanceLexer //Ate package
      DebugPrint println "'package' ate"

      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        QualId parse builder
        DebugPrint println "quilId ate"
      } else {
        builder error "expected qualified identifier"
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

      var isError = false;
      var isEnd = false;
      while (!builder.eof && !isError && !isEnd) {

        isError = false

        while (BNF.firstStatementSeparator.contains(builder.getTokenType)) {
          StatementSeparator parse builder
          DebugPrint println ("TopStatSeq: StatementSeparator parsed, token " + builder.getTokenType)
        }

        if (BNF.firstTopStat.contains(builder.getTokenType)) {
          TopStat.parse(builder)
        }

        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType) || builder.eof) {
          isEnd = true;
        }

        if (!isEnd && !BNF.firstStatementSeparator.contains(builder.getTokenType)) {
          isError = true;
          builder error "top statement declaration error"

          if (BNF.firstTopStat.contains(builder.getTokenType)) {
            isError = false;
          } else {
            builder.advanceLexer
            isError = false;
             tryParseTopStat(builder)
          }
        }

        DebugPrint println ("TopStatSeq: token " + builder.getTokenType)

      }
    }

    def tryParseTopStat (builder : PsiBuilder) : Unit = {

      var isParsed = false;
      while (!builder.eof() && !isParsed){
        if (BNF.firstTopStat.contains(builder.getTokenType)) {
          TopStat parse builder
          isParsed = true
        } else {
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
 
/*
 *  [package QualId StatementSeparator]
 */

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

/*
 *  Packaging ::= package QualId ‘{’ TopStatSeq ‘}’
 */

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
        } else {
          builder error "expected qualified identifier"
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


/*
 *  QualId ::= id {‘.’ id}
 */

    object QualId extends Constr {
      override def getElementType = ScalaElementTypes.QUAL_ID
      override def parseBody(builder : PsiBuilder) : Unit = {
       //todo: change to simple qualID
       StableId.parse(builder)
      }
    }
}
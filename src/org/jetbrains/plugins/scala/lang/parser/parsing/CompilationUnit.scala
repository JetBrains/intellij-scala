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
    
    if (builder.getTokenType.equals(ScalaTokenTypes.kPACKAGE)) {
        val packChooseMarker = builder.mark()
        builder.advanceLexer //Ate package
        DebugPrint println "'package' ate"

        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          QualId parse builder
          DebugPrint println "quilId ate"
        }

        DebugPrint println "after parsing qualId" + builder.getTokenType
        if (builder eof) {
          packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
        }

        if (BNF.firstStatementSeparator.contains(builder.getTokenType)) {
          StatementSeparator parse builder

          packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
          DebugPrint println "package stmt done"

          TopStatSeq parse builder

          return
        }


        val packageBlockMarker = builder.mark()
        if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
          DebugPrint println "begin of packaging "

          TopStatSeq parse builder

          if (builder.getTokenType.equals(ScalaTokenTypes.tRBRACE)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)

            packageBlockMarker.done(ScalaElementTypes.PACKAGING_BLOCK)
            
            packChooseMarker.done(ScalaElementTypes.PACKAGING)
            DebugPrint println "end of packaging "
            return
          } else {
            builder.error("expected '}'")
            packageBlockMarker.drop()
            packChooseMarker.drop()
            return
          }

          packChooseMarker.drop()
          return
        }

        builder.error("expected top statement")
        packChooseMarker.drop()
        return
      }

      TopStatSeq parse builder
    }


  object TopStatSeq extends ConstrWithoutNode {
    //override def getElementType = ScalaElementTypes.TOP_STAT_SEQ

    override def parseBody (builder: PsiBuilder): Unit = {

      if (BNF.firstTopStat.contains(builder.getTokenType)) {
        TopStat.parse(builder)
        DebugPrint println "1. next token in topstat: " + builder.getTokenType
      }

      while (!builder.eof() && (BNF.firstStatementSeparator.contains(builder.getTokenType))){
        StatementSeparator parse builder

        if (BNF.firstTopStat.contains(builder.getTokenType)) {
          TopStat.parse(builder)
        }
          DebugPrint println "2. next token in topstat: " + builder.getTokenType
      }
    }
  }

  object TopStat {
    def parse(builder: PsiBuilder): Unit = {

      if (builder.getTokenType.equals(ScalaTokenTypes.kIMPORT)){
        Import.parse(builder)
        return
      }

      if (builder.getTokenType.equals(ScalaTokenTypes.kPACKAGE)){
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

      if (isTmpl && !(builder.getTokenType.equals(ScalaTokenTypes.kCASE) || BNF.firstTmplDef.contains(builder.getTokenType))) {
        builder.error("wrong type declaration")
        tmplDefMarker.drop()
        return
      }

      if (builder.getTokenType.equals(ScalaTokenTypes.kCASE) || BNF.firstTmplDef.contains(builder.getTokenType)) {
        tmplDefMarker.done(TmplDef.parseBodyNode(builder))
        return
      }

      tmplDefMarker.drop()
      builder error "wrong top statement declaration"
      return
    }
  }
 
/*
    object Package extends Constr {
      override def getElementType = ScalaElementTypes.PACKAGE_STMT

      override def parseBody(builder: PsiBuilder): Unit = {

        builder.getTokenType() match {
          case ScalaTokenTypes.kPACKAGE => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kPACKAGE)

            builder.getTokenType() match {
              case ScalaTokenTypes.tIDENTIFIER => {
                QualId parse builder
              }

              case _ => builder.error("Wrong package name")
            }
          }

          case _ => { builder.error("expected token 'package'") }
        }
    }
  }
 */
    object Packaging extends Constr {
      override def getElementType = ScalaElementTypes.PACKAGING

      override def parseBody(builder: PsiBuilder) : Unit = {

        builder.getTokenType() match {
          case ScalaTokenTypes.kPACKAGE => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kPACKAGE)

            ParserUtils.eatConstr(builder, QualId, ScalaElementTypes.QUAL_ID)

              //Console.println("expected { : " + builder.getTokenType())
              if ( ScalaTokenTypes.tLBRACE.equals(builder.getTokenType()) ){

                ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
                //ParserUtils.eatConstr(builder, TopStatSeq, ScalaElementTypes.TOP_STAT_SEQ)
                TopStatSeq parse builder

              } else {
                builder.error("expected '{'")
                return
              }

              if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType())) {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
              } else {
                builder.error("expected '}'")
                return
              }
          }

          case _ => {
            builder.error("expected 'package") }
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
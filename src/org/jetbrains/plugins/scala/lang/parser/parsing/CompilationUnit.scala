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

object CompilationUnit extends Constr {
  override def getElementType = ScalaElementTypes.COMPILATION_UNIT
  override def parseBody (builder : PsiBuilder) : Unit = {

    //Console.println("token type : " + builder.getTokenType())
    builder.getTokenType() match {
      //possible package statement
      case ScalaTokenTypes.kPACKAGE => {
        ////Console.println("top level package handle")
        //ParserUtils.eatConstr(builder, Package, ScalaElementTypes.PACKAGE_STMT)
        val packChooseMarker = builder.mark()
        //Console.println("exp package " + builder.getTokenType)
        builder.advanceLexer //Ate package

        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
          QualId.parse(builder)
        }

        //

        //Console.println("terminator or semicolon " + builder.getTokenType)
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR
             | ScalaTokenTypes.tSEMICOLON
             => {
            //Console.println("package parse")
            packChooseMarker.rollbackTo()
            Package.parse(builder)

            builder.getTokenType match {
              case ScalaTokenTypes.tLINE_TERMINATOR => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
              }

              case ScalaTokenTypes.tSEMICOLON => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tSEMICOLON)
              }

              case _ => { builder.error("expected ';' or line terminator")}
            }

          }

          case _ => { packChooseMarker.rollbackTo() }
        }
        ////Console.println("top level package handled")
      }

      case _=> {}
    }

    //Console.println("token type : " + builder.getTokenType())


        //Console.println("TopStatSeq invoke ")
        TopStatSeq.parse(builder)
        //Console.println("TopStatSeq invoked ")

  }


  object TopStatSeq extends ConstrWithoutNode {
    //override def getElementType = ScalaElementTypes.TOP_STAT_SEQ

    override def parseBody (builder: PsiBuilder): Unit = {

      if (BNF.firstTopStat.contains(builder.getTokenType)) {
        TopStat.parse(builder)
      }

      while (!builder.eof() && (BNF.firstStatementSeparator.contains(builder.getTokenType))){
        StatementSeparator parse builder

        if (BNF.firstTopStat.contains(builder.getTokenType)) {
          TopStat.parse(builder)
        }
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
    }
  }
 

    object Package extends Constr {
      override def getElementType = ScalaElementTypes.PACKAGE_STMT

      override def parseBody(builder: PsiBuilder): Unit = {

        builder.getTokenType() match {
          case ScalaTokenTypes.kPACKAGE => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kPACKAGE)

            builder.getTokenType() match {
              case ScalaTokenTypes.tIDENTIFIER => {
                //val qualIdMarker = builder.mark()
                //ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
                ParserUtils.eatConstr(builder, QualId, ScalaElementTypes.QUAL_ID)
                //qualIdMarker.done(ScalaElementTypes.QUAL_ID)

                StatementSeparator parse builder
              }

              case _ => builder.error("Wrong package name")
            }
          }

          case _ => { builder.error("expected token 'package'") }
        }
    }
  }

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

              } else builder.error("expected '{'")

              if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType())) {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
              } else builder.error("expected '}'")
          }

          case _ => { builder.error("expected 'package") }
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
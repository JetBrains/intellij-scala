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

object CompilationUnit extends Constr{
  override def parse (builder : PsiBuilder) : Unit = {

    Console.println("token type : " + builder.getTokenType())
    builder.getTokenType() match {
      //possible package statement
      case ScalaTokenTypes.kPACKAGE => {
        //Console.println("top level package handle")
        //ParserUtils.eatConstr(builder, Package, ScalaElementTypes.PACKAGE_STMT)
        val packChooseMarker = builder.mark()
        Console.println("exp package " + builder.getTokenType)
        builder.advanceLexer //Ate package

        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
          QualId.parse(builder)
        }

        //

        Console.println("terminator or semicolon " + builder.getTokenType)
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR
             | ScalaTokenTypes.tSEMICOLON
             => {
            Console.println("package parse")
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
        //Console.println("top level package handled")
      }

      case _=> {}
    }

    Console.println("token type : " + builder.getTokenType())   
    builder.getTokenType() match {
      case ScalaTokenTypes.tLSQBRACKET
         | ScalaTokenTypes.kABSTRACT
         | ScalaTokenTypes.kFINAL
         | ScalaTokenTypes.kSEALED
         | ScalaTokenTypes.kIMPLICIT
         | ScalaTokenTypes.kOVERRIDE
         | ScalaTokenTypes.kPRIVATE
         | ScalaTokenTypes.kPROTECTED
         | ScalaTokenTypes.kCASE
         | ScalaTokenTypes.kCLASS
         | ScalaTokenTypes.kOBJECT
         | ScalaTokenTypes.kTRAIT
         | ScalaTokenTypes.kIMPORT
         | ScalaTokenTypes.kPACKAGE
         | _
         => {
        Console.println("TopStatSeq invoke ")
        //ParserUtils.eatConstr(builder, TopStatSeq, ScalaElementTypes.TOP_STAT_SEQ)
        TopStatSeq.parse(builder)

        Console.println("TopStatSeq invoked ")

      }

      case _ => {builder.error("wrong top declaration")}
    }

  }


  object TopStatSeq extends Constr {
    override def parse(builder: PsiBuilder): Unit = {
      val topStatSeq = builder.mark()

      Console.println("single top stat handle")
      TopStat.parse(builder)
      Console.println("single top stat handled")


      //Console.println("token type, semi or lt " + builder.getTokenType())
      while (!builder.eof() && (builder.getTokenType().equals(ScalaTokenTypes.tSEMICOLON)
          || builder.getTokenType().equals(ScalaTokenTypes.tLINE_TERMINATOR))) {

        Console.println("statement separator handle")
        ParserUtils.eatConstr(builder, StatementSeparator, ScalaElementTypes.STATEMENT_SEPARATOR)

        Console.println("statement separator handled")

        Console.println("single top stat handle")

        TopStat.parse(builder)

        Console.println("single top stat handled")

       // Console.println("after topStat token is " + builder.getTokenType())
      }
     topStatSeq.done(ScalaElementTypes.TOP_STAT_SEQ)
    }
  }

  object TopStat extends Constr {
    override def parse(builder: PsiBuilder): Unit = {
      val topStatMarker = builder.mark()

      //Console.println("token type : " + builder.getTokenType())
      if (builder.eof) {
        topStatMarker.done(ScalaElementTypes.TOP_STAT)
        return
      }

      if (builder.getTokenType.equals(ScalaTokenTypes.kIMPORT)){
        Console.println("parse import")
        Import.parse(builder)
        topStatMarker.done(ScalaElementTypes.TOP_STAT)
        return
      }

      if (builder.getTokenType.equals(ScalaTokenTypes.kPACKAGE)){
        Console.println("parse packaging")
        Packaging.parse(builder)
        topStatMarker.done(ScalaElementTypes.TOP_STAT)
        return
      }

      var isTmpl = false

      while (builder.getTokenType.equals(ScalaTokenTypes.tLSQBRACKET)) {
        Console.println("parse attribute clause")
        isTmpl = true
        AttributeClause.parse(builder)
      }

      while (BNF.firstModifier.contains(builder.getTokenType)) {
        Console.println("parse modifier")
        isTmpl = true
        Modifier.parse(builder)
      }

      if (isTmpl && !(builder.getTokenType.equals(ScalaTokenTypes.kCASE) || BNF.firstTmplDef.contains(builder.getTokenType))) {
        builder.error("wrong type declaration")
        topStatMarker.done(ScalaElementTypes.TOP_STAT)
        return
      }

      if (builder.getTokenType.equals(ScalaTokenTypes.kCASE) || BNF.firstTmplDef.contains(builder.getTokenType)) {
        Console.println("parse tmplDef")
        TmplDef.parse(builder)
        topStatMarker.done(ScalaElementTypes.TOP_STAT)
        return
      }
    

      //if parse nothing
      topStatMarker.done(ScalaElementTypes.TOP_STAT)
    }
  }
 

    object Package extends Constr {
      override def parse(builder: PsiBuilder): Unit = {

        builder.getTokenType() match {
          case ScalaTokenTypes.kPACKAGE => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kPACKAGE)

            builder.getTokenType() match {
              case ScalaTokenTypes.tIDENTIFIER => {
                //val qualIdMarker = builder.mark()
                //ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
                ParserUtils.eatConstr(builder, QualId, ScalaElementTypes.QUAL_ID)
                //qualIdMarker.done(ScalaElementTypes.QUAL_ID)

                ParserUtils.eatConstr(builder, StatementSeparator, ScalaElementTypes.STATEMENT_SEPARATOR)
              }

              case _ => builder.error("Wrong package name")
            }
          }

          case _ => { builder.error("expected token 'package'") }
        }
    }
  }

    object Packaging extends Constr {
      override def parse(builder: PsiBuilder) : Unit = {
      val packagingMarker = builder.mark()

        builder.getTokenType() match {
          case ScalaTokenTypes.kPACKAGE => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kPACKAGE)

            ParserUtils.eatConstr(builder, QualId, ScalaElementTypes.QUAL_ID)

              Console.println("expected { : " + builder.getTokenType())
              if ( ScalaTokenTypes.tLBRACE.equals(builder.getTokenType()) ){

                ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
                ParserUtils.eatConstr(builder, TopStatSeq, ScalaElementTypes.TOP_STAT_SEQ)

              } else builder.error("expected '{'")

              if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType())) {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
              } else builder.error("expected '}'")
          }

          case _ => { builder.error("expected 'package") }
        }

        packagingMarker.done(ScalaElementTypes.PACKAGING)
      }
    }

    object QualId extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
       //todo: change to simple qualID
       StableId.parse(builder)
      }
    }
}
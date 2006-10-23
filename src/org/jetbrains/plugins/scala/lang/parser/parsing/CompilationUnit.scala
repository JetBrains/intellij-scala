package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AttributeClause
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
//import org.jetbrains.plugins.scala.lang.parser.parsing.base._
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
        Console.println("top level package handle")
        ParserUtils.eatConstr(builder, Package, ScalaElementTypes.PACKAGE_STMT)
        Console.println("top level package handled")
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
        TopStatSeq.parse(builder)
        Console.println("TopStatSeq invoked ")

      }

      case _ => {builder.error("wrong top declaration")}
    }

  }


  object TopStatSeq extends Constr{
    override def parse(builder: PsiBuilder): Unit = {

      Console.println("single top stat handle")
      ParserUtils.eatConstr(builder, TopStat, ScalaElementTypes.TOP_STAT)
      Console.println("single top stat handled")

      Console.println("token type " + builder.getTokenType())
      while (builder.getTokenType().equals(ScalaTokenTypes.tSEMICOLON)
          || builder.getTokenType().equals(ScalaTokenTypes.tLINE_TERMINATOR)){

        Console.println("statement separator handle")
        ParserUtils.eatConstr(builder, StatementSeparator, ScalaElementTypes.STATEMENT_SEPARATOR)
        Console.println("statement separator handled")

        ParserUtils.rollForward(builder)

        Console.println("sungle top stat handle")
        ParserUtils.eatConstr(builder, TopStat, ScalaElementTypes.TOP_STAT)
        Console.println("sungle top stat handled")
      }


    }
  }

  object TopStat extends Constr {
    override def parse(builder: PsiBuilder): Unit = {

      Console.println("token type : " + builder.getTokenType())
       builder.getTokenType() match {
        case ScalaTokenTypes.tLSQBRACKET => {
          Console.println("attribute clause handle")
          ParserUtils.eatConstr(builder, AttributeClause, ScalaElementTypes.ATTRIBUTE_CLAUSE)
          Console.println("attribute clause handled")
        }

        case ScalaTokenTypes.kABSTRACT
           | ScalaTokenTypes.kFINAL
           | ScalaTokenTypes.kSEALED
           | ScalaTokenTypes.kIMPLICIT
           | ScalaTokenTypes.kOVERRIDE
           | ScalaTokenTypes.kPRIVATE
           | ScalaTokenTypes.kPROTECTED
           => {
           Console.println("modifier handle")
           ParserUtils.eatConstr(builder, Modifier, ScalaElementTypes.MODIFIER)
           Console.println("modifier handled")
        }

        case ScalaTokenTypes.kCASE
           | ScalaTokenTypes.kCLASS
           | ScalaTokenTypes.kOBJECT
           | ScalaTokenTypes.kTRAIT
           => {
           val tmplDefMarker = builder.mark()
           Console.println("tmpl handle")
           ParserUtils.eatConstr(builder, TmplDef, ScalaElementTypes.TMPL_DEF)
           ParserUtils.rollForward(builder)
           Console.println("tmpl handled")
        }

        case ScalaTokenTypes.kIMPORT => {
           Console.println("import handled")
           ParserUtils.eatConstr(builder, Import, ScalaElementTypes.IMPORT_STMT)
           ParserUtils.rollForward(builder)
           Console.println("import handle")
        }

        case ScalaTokenTypes.kPACKAGE => {
           //todo: packaging
           Console.println("packaging handle")
           ParserUtils.eatConstr(builder, Packaging, ScalaElementTypes.PACKAGE_STMT)
           ParserUtils.rollForward(builder)
           Console.println("packaging handled")
        }

        case _ => {
          //ParserUtils.rollForward(builder)
           /*
          if (!builder.eof()) builder.error("expected end of file")
          */
        }
      }
    }
  }
 

    object Package extends Constr {
      override def parse(builder: PsiBuilder): Unit = {

        builder.getTokenType() match {
          case ScalaTokenTypes.kPACKAGE => {
            ParserUtils.eatElement(builder, ScalaElementTypes.PACKAGE)

            builder.getTokenType() match {
              case ScalaTokenTypes.tIDENTIFIER => {
                //val qualIdMarker = builder.mark()
                //ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
                ParserUtils.eatConstr(builder, QualId, ScalaElementTypes.QUAL_ID)
                //qualIdMarker.done(ScalaElementTypes.QUAL_ID)
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
      }
    }

    object QualId extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
       //todo: change to simple qualID
       StableId.parse(builder)
      }
    }
}
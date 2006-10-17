package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Package
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Import
import org.jetbrains.plugins.scala.lang.parser.parsing.base
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AttributeClause
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.base._



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
object CompilationUnit {
  def parse(builder: PsiBuilder): Unit = {

    Console.println("token type : " + builder.getTokenType())
           builder.getTokenType() match {
      //possible package statement
      case ScalaTokenTypes.kPACKAGE => {
        val packageStmtMarker = builder.mark()
        Console.println("top level package handle")
        Package.parse(builder)
        Console.println("top level package handled")
        packageStmtMarker.done(ScalaElementTypes.PACKAGE_STMT)
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


  object TopStatSeq {
    def parse(builder: PsiBuilder): Unit = {

      val topStatMarker = builder.mark()

      Console.println("single top stat handle")
      TopStat.parse(builder)
      Console.println("single top stat handled")

      topStatMarker.done(ScalaElementTypes.TOP_STAT)

      Console.println("token type " + builder.getTokenType())
      while (builder.getTokenType().equals(ScalaTokenTypes.tSEMICOLON)
          || builder.getTokenType().equals(ScalaTokenTypes.tLINE_TERMINATOR)){

        val statamentSeparatorMarker = builder.mark()
        Console.println("statement separator handle")
        StatementSeparator.parse(builder)
        Console.println("statement separator handled")
        statamentSeparatorMarker.done(ScalaElementTypes.STATEMENT_SEPARATOR)

        val topStatMarker = builder.mark()
        Console.println("sungle top stat handle")
        TopStat.parse(builder)
        Console.println("sungle top stat handled")
        topStatMarker.done(ScalaElementTypes.TOP_STAT)
      }
    }
  }

  object TopStat {
    def parse(builder: PsiBuilder): Unit = {

      Console.println("token type : " + builder.getTokenType())
       builder.getTokenType() match {
        case ScalaTokenTypes.tLSQBRACKET => {
          val attributeClauseMarker = builder.mark()
          Console.println("attribute clause handle")
          AttributeClause.parse(builder)
          Console.println("attribute clause handled")
          attributeClauseMarker.done(ScalaElementTypes.ATTRIBUTE_CLAUSE)
        }

        case ScalaTokenTypes.kABSTRACT
           | ScalaTokenTypes.kFINAL
           | ScalaTokenTypes.kSEALED
           | ScalaTokenTypes.kIMPLICIT
           | ScalaTokenTypes.kOVERRIDE
           | ScalaTokenTypes.kPRIVATE
           | ScalaTokenTypes.kPROTECTED
           => {
           val modifierMarker = builder.mark()
           Console.println("modifier handle")
           Modifier.parse(builder)
           Console.println("modifier handled")
           modifierMarker.done(ScalaElementTypes.MODIFIER)  
        }

        case ScalaTokenTypes.kCASE
           | ScalaTokenTypes.kCLASS
           | ScalaTokenTypes.kOBJECT
           | ScalaTokenTypes.kTRAIT
           => {
           val tmplDefMarker = builder.mark()
           Console.println("tmpl handle")
           TmplDef.parse(builder)
           Console.println("tmpl handled")
           tmplDefMarker.done(ScalaElementTypes.TMPL_DEF)
        }

        case ScalaTokenTypes.kIMPORT => {
           val importMarker = builder.mark()
           Console.println("import handle")
           Import.parse(builder)
           Console.println("import handled")
           importMarker.done(ScalaElementTypes.IMPORT_STMT)
        }

        case ScalaTokenTypes.kPACKAGE => {
           val packageMarker = builder.mark()
           //todo: packaging
           Console.println("package handle")
           Package.parse(builder)
           Console.println("package handled")
           packageMarker.done(ScalaElementTypes.PACKAGE_STMT)
        }

        case _ => {builder.error("wrong top stat")}
      }
    }
  }
}
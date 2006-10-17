package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top._
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

    builder.getTokenType() match {
      //possible package statement
      case ScalaTokenTypes.kPACKAGE => {
        val packageStmtMarker = builder.mark()
        Package.parse(builder)
        packageStmtMarker.done(ScalaElementTypes.PACKAGE_STMT)
      }

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
         => {

        TopStatSeq.parse(builder)
      }

      case _ => {builder.error("wrong top declaration")}
    }

  }


  object TopStatSeq {
    def parse(builder: PsiBuilder): Unit = {
      val topStatMarker = builder.mark()
      TopStat.parse(builder)
      topStatMarker.done(ScalaElementTypes.TOP_STAT)

      while (builder.getTokenType().equals(ScalaTokenTypes.tSEMICOLON)
          || builder.getTokenType().equals(ScalaTokenTypes.tLINE_TERMINATOR)){

        val statamentSeparatorMarker = builder.mark()
        StatementSeparator.parse(builder)
        statamentSeparatorMarker.done(ScalaElementTypes.STATEMENT_SEPARATOR)

        val topStatMarker = builder.mark()
        TopStat.parse(builder)
        topStatMarker.done(ScalaElementTypes.TOP_STAT)
      }
    }
  }

  object TopStat {
    def parse(builder: PsiBuilder): Unit = {
      builder.getTokenType() match {
        case ScalaTokenTypes.tLSQBRACKET => {
          val attributeClauseMarker = builder.mark()
          AttributeClause.parse(builder)
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
           Modifier.parse(builder)
           modifierMarker.done(ScalaElementTypes.MODIFIER)  
        }

        case ScalaTokenTypes.kCASE
           | ScalaTokenTypes.kCLASS
           | ScalaTokenTypes.kOBJECT
           | ScalaTokenTypes.kTRAIT
           => {
           val tmplDefMarker = builder.mark()
           TmplDef.parse(builder)
           tmplDefMarker.done(ScalaElementTypes.TMPL_DEF)
        }

        case ScalaTokenTypes.kIMPORT => {
           val importMarker = builder.mark()
           Import.parse(builder)
           importMarker.done(ScalaElementTypes.IMPORT)
        }

        case ScalaTokenTypes.kPACKAGE => {
           val packageMarker = builder.mark()
           Package.parse(builder)
           packageMarker.done(ScalaElementTypes.PACKAGE)
        }

        case _ => {builder.error("wrong top stat")}
      }
    }
  }
}
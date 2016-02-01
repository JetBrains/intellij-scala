package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateStat
import org.jetbrains.plugins.scala.lang.parser.util.{ParserPatcher, ParserUtils}

import scala.annotation.tailrec

/**
  * @author Alexander Podkhalyuzin
  *         Date: 05.02.2008
  */

/*
*  TopStat ::= {Annotation} {Modifier} -> TmplDef (it's mean that all parsed in TmplDef)
*            | Import
*            | Packaging
*/
object TopStat extends TopStat {
  override protected val tmplDef = TmplDef
  override protected val templateStat = TemplateStat
  override protected val packaging = Packaging
  override protected val packageObject = PackageObject
}

trait TopStat {
  protected val tmplDef: TmplDef
  protected val templateStat: TemplateStat
  protected val packageObject: PackageObject
  protected val packaging: Packaging

  @tailrec
  final def parse(builder: ScalaPsiBuilder, state: Int): Int = {
    val patcher = ParserPatcher.getSuitablePatcher(builder)

    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT =>
        Import parse builder
        ParserState.ADDITIONAL_STATE
      case ScalaTokenTypes.kPACKAGE =>
        if (state == 2) ParserState.EMPTY_STATE
        else {
          if (ParserUtils.lookAhead(builder, ScalaTokenTypes.kPACKAGE, ScalaTokenTypes.kOBJECT)) {
            if (packageObject parse builder) ParserState.FILE_STATE
            else ParserState.EMPTY_STATE
          } else {
            if (packaging parse builder) ParserState.FILE_STATE
            else ParserState.EMPTY_STATE
          }
        }
      case _ if patcher.parse(builder) =>
        if (!builder.eof()) parse(builder, state) else ParserState.SCRIPT_STATE
      case _ =>
        state match {
          case ParserState.EMPTY_STATE => if (!tmplDef.parse(builder)) {
            if (!templateStat.parse(builder)) ParserState.EMPTY_STATE
            else ParserState.SCRIPT_STATE
          } else ParserState.ADDITIONAL_STATE
          case ParserState.FILE_STATE => if (!tmplDef.parse(builder)) ParserState.EMPTY_STATE
          else ParserState.FILE_STATE
          case ParserState.SCRIPT_STATE => if (!templateStat.parse(builder)) ParserState.EMPTY_STATE
          else ParserState.SCRIPT_STATE
        }
    }
  }
}
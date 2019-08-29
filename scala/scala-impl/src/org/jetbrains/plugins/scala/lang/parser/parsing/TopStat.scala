package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Export, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateStat
import org.jetbrains.plugins.scala.lang.parser.util.ParserPatcher

import scala.annotation.tailrec

/**
 * [[TopStat]] ::= {Annotation} {Modifier} -> [[TmplDef]] (it's mean that all parsed in TmplDef)
 * | [[Import]]
 * | [[Export]]
 * | [[Packaging]]
 *
 * @author Alexander Podkhalyuzin
 *         Date: 05.02.2008
 */
object TopStat {

  import ParserState._
  import lexer.ScalaTokenTypes._

  @tailrec
  final def parse(state: Int)
                 (implicit builder: ScalaPsiBuilder): Int = {
    val patcher = ParserPatcher.getSuitablePatcher(builder)

    builder.getTokenType match {
      case `kIMPORT` =>
        Import()
        ADDITIONAL_STATE
      case lexer.ScalaTokenType.IsExport() =>
        Export()
        ADDITIONAL_STATE
      case `kPACKAGE` =>
        if (state == 2) EMPTY_STATE
        else {
          if (builder.lookAhead(kPACKAGE, kOBJECT)) {
            if (PackageObject.parse(builder)) FILE_STATE
            else EMPTY_STATE
          } else {
            if (Packaging.parse(builder)) FILE_STATE
            else EMPTY_STATE
          }
        }
      case _ if patcher.parse(builder) =>
        if (!builder.eof()) parse(state)
        else SCRIPT_STATE
      case _ =>
        state match {
          case EMPTY_STATE =>
            if (!TmplDef()) {
              if (!TemplateStat()) EMPTY_STATE else SCRIPT_STATE
            } else
              ADDITIONAL_STATE
          case FILE_STATE =>
            if (!TmplDef()) EMPTY_STATE else FILE_STATE
          case SCRIPT_STATE =>
            if (!TemplateStat()) EMPTY_STATE else SCRIPT_STATE
        }
    }
  }
}
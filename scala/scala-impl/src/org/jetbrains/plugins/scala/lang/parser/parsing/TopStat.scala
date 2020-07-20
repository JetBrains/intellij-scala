package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Export, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateStat

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
  import lexer.ScalaTokenType._
  import lexer.ScalaTokenTypes._

  @tailrec
  final def parse(state: ParserState)
                 (implicit builder: ScalaPsiBuilder): Option[ParserState] =
    builder.getTokenType match {
      case `kIMPORT` =>
        Import()
        None
      case ExportKeyword =>
        Export()
        None
      case `kPACKAGE` =>
        if (state == SCRIPT_STATE) Some(EMPTY_STATE)
        else {
          if (builder.lookAhead(kPACKAGE, ObjectKeyword)) {
            if (PackageObject.parse(builder)) Some(FILE_STATE)
            else Some(EMPTY_STATE)
          } else {
            if (Packaging.parse(builder)) Some(FILE_STATE)
            else Some(EMPTY_STATE)
          }
        }
      case _ if builder.skipExternalToken() =>
        if (!builder.eof()) parse(state) else Some(SCRIPT_STATE)
      case _ =>
        state match {
          case EMPTY_STATE =>
            if (TmplDef.parse(builder)) None
            else if (TemplateStat.parse(builder)) Some(SCRIPT_STATE)
            else Some(EMPTY_STATE)
          case FILE_STATE =>
            if (TmplDef.parse(builder)) Some(FILE_STATE)
            else Some(EMPTY_STATE)
          case SCRIPT_STATE =>
            if (TemplateStat.parse(builder)) Some(SCRIPT_STATE)
            else Some(EMPTY_STATE)
        }
    }
}
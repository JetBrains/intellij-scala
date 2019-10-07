package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Export, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateStat

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

  @annotation.tailrec
  final def parse(state: Int)
                 (implicit builder: ScalaPsiBuilder): Int =
    builder.getTokenType match {
      case `kIMPORT` =>
        Import()
        ADDITIONAL_STATE
      case ExportKeyword =>
        Export()
        ADDITIONAL_STATE
      case `kPACKAGE` =>
        if (state == SCRIPT_STATE) EMPTY_STATE
        else {
          if (builder.lookAhead(kPACKAGE, ObjectKeyword)) {
            if (PackageObject.parse(builder)) FILE_STATE
            else EMPTY_STATE
          } else {
            if (Packaging.parse(builder)) FILE_STATE
            else EMPTY_STATE
          }
        }
      case _ if builder.skipExternalToken() =>
        if (!builder.eof()) parse(state) else SCRIPT_STATE
      case _ =>
        state match {
          case EMPTY_STATE => if (!TmplDef.parse(builder)) {
            if (!TemplateStat.parse(builder)) EMPTY_STATE
            else SCRIPT_STATE
          } else ADDITIONAL_STATE
          case FILE_STATE => if (!TmplDef.parse(builder)) EMPTY_STATE
          else FILE_STATE
          case SCRIPT_STATE => if (!TemplateStat.parse(builder)) EMPTY_STATE
          else SCRIPT_STATE
        }
    }
}
package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Export, Extension, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Annotation, Expr}
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{Dcl, Def, EmptyDcl}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateStat

import scala.annotation.tailrec

/**
 * [[TopStat]] ::= {Annotation} {Modifier} -> [[TmplDef]] (it's mean that all parsed in TmplDef)
 * | [[Import]]
 * | [[Export]]
 * | [[Extension]]
 * | [[Packaging]]
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
      case _ if Extension() =>
        None
      case `kPACKAGE` =>
        if (state == SCRIPT_STATE) Some(EMPTY_STATE)
        else {
          if (builder.lookAhead(kPACKAGE, ObjectKeyword)) {
            if (PackageObject()) Some(FILE_STATE)
            else Some(EMPTY_STATE)
          } else {
            if (Packaging()) Some(FILE_STATE)
            else Some(EMPTY_STATE)
          }
        }
      case _ if builder.skipExternalToken() =>
        if (!builder.eof()) parse(state) else Some(SCRIPT_STATE)
      case _ =>
        state match {
          case EMPTY_STATE =>
            if (TmplDef()) None
            else if (Def() || Dcl() || EmptyDcl()) {
              // in scala 3, definitions and declarations are allowed on top level
              // so we still don't know if it is a script or a file
              if (builder.isScala3) None else Some(SCRIPT_STATE)
            } else if (Expr()) {
              Some(SCRIPT_STATE)
            } else incompleteAnnotationOrFallback()
          case FILE_STATE if builder.isScala3 =>
            if (TemplateStat()) Some(FILE_STATE)
            else incompleteAnnotationOrFallback()
          case FILE_STATE =>
            if (TmplDef()) Some(FILE_STATE)
            else incompleteAnnotationOrFallback()
          case SCRIPT_STATE =>
            if (TemplateStat()) Some(SCRIPT_STATE)
            else incompleteAnnotationOrFallback()
        }
    }

  def incompleteAnnotationOrFallback()(implicit builder: ScalaPsiBuilder): Some[EMPTY_STATE.type] = {
    Annotation.skipUnattachedAnnotations(ErrMsg("missing.toplevel.statement.for.annotation"))
    Some(EMPTY_STATE)
  }
}
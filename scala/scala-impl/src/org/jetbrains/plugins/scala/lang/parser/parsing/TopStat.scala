package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Export, Extension, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateStat
import org.jetbrains.plugins.scala.lang.scalacli.parser.ScalaCliElementTypes.SCALA_CLI_DIRECTIVE

import scala.annotation.tailrec

/**
 * [[TopStat]] ::= {Annotation} {Modifier} -> [[TmplDef]] (it's mean that all parsed in TmplDef)
 * | [[Import]]
 * | [[Export]]
 * | [[Extension]]
 * | [[Packaging]]
 */
object TopStat {

  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType._
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  @tailrec
  final def parse()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case `kIMPORT` =>
        Import()
        true
      case ExportKeyword =>
        Export()
        true
      case `kPACKAGE` =>
        if (builder.lookAhead(kPACKAGE, ObjectKeyword))
          PackageObject()
        else
          Packaging()
      case _ if Extension() =>
        true
      case _ if builder.skipExternalToken() =>
        if (builder.eof())
          true
        else
          TopStat.parse()
      case `SCALA_CLI_DIRECTIVE` =>
        builder.advanceLexer()
        true
      case _ =>
        //For simplicity parse all definitions for Scala3, Scala2
        //even though Scala 2 doesn't support top-level definitions.
        //Also parse expressions, even though the file might be not a worksheet
        TemplateStat()
    }
}
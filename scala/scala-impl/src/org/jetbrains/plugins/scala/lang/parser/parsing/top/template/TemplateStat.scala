package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Export, Extension, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Annotation, Expr}
import org.jetbrains.plugins.scala.lang.parser.parsing.statements._

sealed abstract class Stat extends ParsingRule {

  import lexer.ScalaTokenType.ExportKeyword
  import lexer.ScalaTokenTypes.kIMPORT

  override final def parse(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case `kIMPORT` => Import()
      case ExportKeyword => Export()
      case _ if Extension() => true
      case _ =>
        parseDeclaration() ||
          EmptyDcl() ||
          Expr() ||
          Annotation.skipUnattachedAnnotations(ErrMsg("missing.statement.for.annotation"))
    }

  protected def parseDeclaration()(implicit builder: ScalaPsiBuilder): Boolean =
    Def() || Dcl()
}

/**
 * Scala2
 * {{{
 * TemplateStat  ::=  Import
 *                 |  {Annotation [nl]} {Modifier} Def
 *                 |  {Annotation [nl]} {Modifier} Dcl
 *                 |  Expr
 * }}}
 *
 * Scala3:
 * {{{
 * TemplateStat  ::=  Import
 *                 |  Export
 *                 |  {Annotation [nl]} {Modifier} Def
 *                 |  {Annotation [nl]} {Modifier} Dcl
 *                 |  Extension
 *                 |  Expr1
 *                 |  EndMarker
 * }}}
 *
 * NOTE: EndMarker is parsed inside TemplateBody
 */
object TemplateStat extends Stat

/**
 * {{{
 *   EnumStat ::=  TemplateStat
 *              |  {Annotation [nl]} {Modifier} EnumCase
 * }}}
 */
object EnumStat extends Stat {

  override protected def parseDeclaration()(implicit builder: ScalaPsiBuilder): Boolean =
    EnumCase() || super.parseDeclaration()
}

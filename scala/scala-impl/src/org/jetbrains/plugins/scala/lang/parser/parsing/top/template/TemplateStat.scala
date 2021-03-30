package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{End, Export, Extension, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Annotation, Expr, Expr1}
import org.jetbrains.plugins.scala.lang.parser.parsing.statements._

/**
 * @author Alexander Podkhalyuzin
 *         Date: 13.02.2008
 */
sealed abstract class Stat extends ParsingRule {

  import lexer.ScalaTokenType.ExportKeyword
  import lexer.ScalaTokenTypes.kIMPORT

  override final def apply()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case `kIMPORT` => Import()
      case ExportKeyword => Export()
      case _ if Extension() => true
      case _ =>
        parseDeclaration() ||
          EmptyDcl() ||
          Expr()
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

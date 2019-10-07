package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Export, Import}
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
      case _ =>
        parseDeclaration() ||
          EmptyDcl.parse(builder) ||
          Expr.parse(builder)
    }

  protected def parseDeclaration()(implicit builder: ScalaPsiBuilder): Boolean =
    Def.parse(builder) || Dcl.parse(builder)
}

/**
 * [[TemplateStat]] ::= [[Import]]
 * | [[Export]]
 * | { [[Annotation]] [nl]} { [[Modifier]] } [[Def]]
 * | { [[Annotation]] [nl]} { [[Modifier]] } [[Dcl]]
 * | [[Expr]] // TODO [[Expr1]] in Scala 3
 */
object TemplateStat extends Stat

/**
 * [[EnumStat]] ::= [[TemplateStat]]
 * | { [[Annotation]] [nl]} { [[Modifier]] } [[EnumCase]]
 */
object EnumStat extends Stat {

  override protected def parseDeclaration()(implicit builder: ScalaPsiBuilder): Boolean =
    EnumCase() || super.parseDeclaration()
}

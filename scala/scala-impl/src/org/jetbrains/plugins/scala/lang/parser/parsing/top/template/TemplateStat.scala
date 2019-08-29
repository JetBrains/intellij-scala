package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Export, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.statements._

/**
 * [[TemplateStat]] ::= [[Import]]
 * | [[Export]]
 * | {AttributeClause} {Modifier} [[Def]]
 * | {AttributeClause} {Modifier} [[Dcl]]
 * | [[Expr]]
 *
 * @author Alexander Podkhalyuzin
 *         Date: 13.02.2008
 */
object TemplateStat extends ParsingRule {

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case lexer.ScalaTokenTypes.kIMPORT =>
        Import()
        true
      case lexer.ScalaTokenType.IsExport() =>
        Export()
        true
      case _ =>
        Def.parse(builder) ||
          Dcl.parse(builder) ||
          EmptyDcl.parse(builder) ||
          Expr.parse(builder)
    }
}
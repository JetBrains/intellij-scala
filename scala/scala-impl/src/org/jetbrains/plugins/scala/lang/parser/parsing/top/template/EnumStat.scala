package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.EnumCase

/**
 * [[EnumStat]] ::= [[TemplateStat]] | { [[Annotation]] [nl]} { [[Modifier]] } [[EnumCase]]
 */
object EnumStat extends ParsingRule {

  // TODO Annotation Modifier
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean =
    TemplateStat.parse(builder) || EnumCase()
}

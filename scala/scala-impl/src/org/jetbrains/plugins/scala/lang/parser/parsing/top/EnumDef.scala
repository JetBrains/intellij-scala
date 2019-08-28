package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.ClassConstr
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.EnumBody

/**
 * [[EnumDef]] ::= id [[ClassConstr]] [[InheritClauses]] [[EnumBody]]
 */
object EnumDef extends ParsingRule {

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer()
        ClassConstr()
        InheritClauses()
        EnumBody()
        true
      case _ =>
        builder.error(ScalaBundle.message("identifier.expected"))
        false
    }
}

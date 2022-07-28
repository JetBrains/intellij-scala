package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._

/*
 * ScalaExpr ::= '{' Block '}'
 */

object ScalaExpr extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START =>
        builder.advanceLexer()
        builder.enableNewlines()
      case _ => return false
    }
    if (!Block.Braceless(stopOnOutdent = false, needNode = true)) {
      builder error ErrMsg("xml.scala.expression.expected")
    }
    CommonUtils.eatAllSemicolons()
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END =>
        builder.advanceLexer()
      case _ => builder error ErrMsg("xml.scala.injection.end.expected")
    }
    builder.restoreNewlinesState()
    true
  }
}
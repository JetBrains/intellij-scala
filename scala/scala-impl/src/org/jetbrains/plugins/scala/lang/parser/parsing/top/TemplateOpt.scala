package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody

object TemplateOpt {

  def parseTemplateBody(implicit builder: ScalaPsiBuilder): Unit =
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE if !builder.twoNewlinesBeforeCurrentToken => TemplateBody.parse(builder)
      case _ =>
    }
}

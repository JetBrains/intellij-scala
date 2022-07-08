package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[Ids]] ::= id { ','  id}
 */
object Ids extends ParsingRule {

  import ScalaElementType._
  import lexer.ScalaTokenTypes._

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    if (parseId()) {
      parseIds()

      marker.done(IDENTIFIER_LIST)
      true
    } else {
      marker.drop()
      false
    }
  }

  private def parseId()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case `tIDENTIFIER` =>
        val marker = builder.mark()
        builder.advanceLexer() // Ate identifier
        marker.done(FIELD_ID)

        true
      case _ =>
        false
    }

  @annotation.tailrec
  private def parseIds()(implicit builder: ScalaPsiBuilder): Unit =
    builder.getTokenType match {
      case `tCOMMA` =>
        builder.advanceLexer() // Ate ,

        if (parseId()) {
          parseIds()
        } else {
          builder.error(ScalaBundle.message("identifier.expected"))
        }
      case _ =>
    }
}
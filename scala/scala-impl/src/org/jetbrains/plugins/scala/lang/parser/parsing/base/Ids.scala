package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[Ids]] ::= [[Ids.Id]] { ','  [[Ids.Id]] }
 *
 * @author Alexander Podkhalyuzin
 *         Date: 15.02.2008
 */
object Ids extends ParsingRule {

  import ScalaElementType.{FIELD_ID, IDENTIFIER_LIST}
  import lexer.ScalaTokenTypes.{tCOMMA, tIDENTIFIER}

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val listMarker = builder.mark()

    if (Id()) {
      var stop = false
      while (stop && builder.getTokenType == tCOMMA) {
        builder.advanceLexer() //Ate ,

        if (!Id()) {
          builder.error(ErrMsg("identifier.expected"))
          stop = true
        }
      }

      listMarker.done(IDENTIFIER_LIST)
      true
    } else {
      listMarker.drop()
      false
    }
  }

  private object Id extends ParsingRule {

    override def parse()(implicit builder: ScalaPsiBuilder): Boolean =
      builder.getTokenType match {
        case `tIDENTIFIER` =>
          val marker = builder.mark()
          builder.advanceLexer() //Ate identifier
          marker.done(FIELD_ID)
          true
        case _ => false
      }
  }
}
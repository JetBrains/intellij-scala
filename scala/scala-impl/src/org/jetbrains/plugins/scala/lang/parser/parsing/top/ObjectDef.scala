package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[ObjectDef]] ::= id [[ClassTemplateOpt]]
 *
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/
object ObjectDef extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate identifier

        //parse extends block
        ClassTemplateOpt.parse(builder)
        true
      case _ =>
        builder.error(ScalaBundle.message("identifier.expected"))
        false
    }
}
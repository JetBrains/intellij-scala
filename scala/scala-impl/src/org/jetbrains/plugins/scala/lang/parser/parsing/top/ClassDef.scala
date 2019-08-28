package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.ClassConstr

/**
 * * [[ClassDef]] ::= id [ [[ClassConstr]] ] [[ClassTemplateOpt]]
 *
 * @author Alexander Podkhalyuzin
 *         Date: 06.02.2008
 */
object ClassDef extends ParsingRule {

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() // Ate identifier
        ClassConstr()

        //parse extends block
        ClassTemplateOpt.parse(builder)
        true
      case _ =>
        builder.error(ErrMsg("identifier.expected"))
        false
    }
}
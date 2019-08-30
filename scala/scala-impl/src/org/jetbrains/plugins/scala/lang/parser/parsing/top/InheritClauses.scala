package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[InheritClauses]] ::= ['extends' [[ConstrApps]] ] ['derives' [[Qual_Id]] { ',' [[Qual_Id]] }]
 */
object InheritClauses extends ParsingRule {

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    ConstrApps()

    val derivesMarker = builder.mark()

    builder.getTokenType match {
      case lexer.ScalaTokenType.IsDerives() =>
        builder.advanceLexer()
        Qual_Id.parse(builder)

        while (builder.getTokenType == lexer.ScalaTokenTypes.tCOMMA) {
          builder.advanceLexer()
          Qual_Id.parse(builder)
        }
      case _ =>
    }

    derivesMarker.done(ScalaElementType.DERIVES_BLOCK)
    true
  }
}

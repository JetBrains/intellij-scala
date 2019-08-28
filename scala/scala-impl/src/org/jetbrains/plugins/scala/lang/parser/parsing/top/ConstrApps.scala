package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[ConstrApps]] ::= ConstrApp {'with' ConstrApp} |  ConstrApp {',' ConstrApp}
 */
object ConstrApps extends ParsingRule {
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val clausesMarker = builder.mark()

    builder.getTokenType match {
      case ScalaTokenTypes.kEXTENDS =>
        builder.advanceLexer()
        ClassParents.parse
      case _ =>
    }

    clausesMarker.done(ScalaElementType.EXTENDS_BLOCK)
    true
  }
}

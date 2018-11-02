package org.jetbrains.plugins.dotty.lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.kWITH
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */

/*
 * WithType ::= AnnotType {`with' AnnotType}
 */
object WithType extends org.jetbrains.plugins.scala.lang.parser.parsing.types.Type {

  override protected def infixType: InfixType.type = InfixType

  override def parse(builder: ScalaPsiBuilder, star: Boolean, isPattern: Boolean): Boolean = {
    def parse() = AnnotType.parse(builder, star, isPattern)

    val marker = builder.mark()
    if (!parse()) {
      marker.drop()
      return false
    }

    var isList = false
    while (builder.getTokenType == kWITH) {
      isList = true
      builder.advanceLexer() // ate token

      if (!parse()) {
        builder.error(ScalaBundle.message("wrong.type"))
      }
    }

    if (isList) marker.done(DottyElementTypes.WITH_TYPE)
    else marker.drop()

    true
  }
}

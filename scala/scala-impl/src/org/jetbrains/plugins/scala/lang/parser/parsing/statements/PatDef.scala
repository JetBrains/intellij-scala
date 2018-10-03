package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * PatDef ::= Pattern2 {',' Pattern2} [':' Type] '=' Expr
 */
//TODO: Rewrite this
object PatDef {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val someMarker = builder.mark
    val pattern2sMarker = builder.mark

    if (!Pattern2.parse(builder, forDef = true)) {
      pattern2sMarker.rollbackTo()
      someMarker.drop()
      return false
    }

    while (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
      builder.checkedAdvanceLexer()

      if (!Pattern2.parse(builder, forDef = true)) {
        pattern2sMarker.rollbackTo()
        someMarker.drop()
        return false
      }
    }

    pattern2sMarker.done(ScalaElementTypes.PATTERN_LIST)

    var hasTypeDcl = false

    if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
      builder.checkedAdvanceLexer()

      if (!Type.parse(builder)) {
        builder error "type declaration expected"
      }

      hasTypeDcl = true
    }
    if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
      someMarker.rollbackTo()
      false
    } else {
      builder.checkedAdvanceLexer()

      if (!Expr.parse(builder)) {
        builder error "expression expected"
      }

      someMarker.drop()
      true
    }
  }
}
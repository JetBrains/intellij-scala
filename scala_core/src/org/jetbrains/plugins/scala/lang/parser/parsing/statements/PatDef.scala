package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import bnf.BNF
import com.intellij.lang.PsiBuilder
import expressions.Expr
import lexer.ScalaTokenTypes
import patterns.Pattern2
import types.Type
import util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * PatDef ::= Pattern2 {',' Pattern2} [':' Type] '=' Expr
 */
//TODO: Rewrite this
object PatDef {
  def parse(builder: PsiBuilder): Boolean = {
    val someMarker = builder.mark
    val pattern2sMarker = builder.mark

    if (BNF.firstPattern2.contains(builder.getTokenType)) {
      Pattern2.parse(builder, true)
    } else {
      builder error "pattern expected"
      pattern2sMarker.rollbackTo
      someMarker.rollbackTo
      return false
    }

    while (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
      ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)

      if (BNF.firstPattern2.contains(builder.getTokenType)) {
        Pattern2.parse(builder, true)
      } else {
        builder error "pattern expected"
        pattern2sMarker.rollbackTo()
        someMarker.drop
        return false
      }
    }

    pattern2sMarker.done(ScalaElementTypes.PATTERN_LIST)

    var hasTypeDcl = false

    if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
      ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

      if (BNF.firstType.contains(builder.getTokenType)) {
        Type parse builder
      } else {
        builder error "type declaration expected"
      }

      hasTypeDcl = true
    }
    if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
      someMarker.rollbackTo
      return false
    } else {
      ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

      if (!Expr.parse(builder)) {
        someMarker.rollbackTo
        return false
      }
      someMarker.drop
      return true
    }

    return false
  }
}
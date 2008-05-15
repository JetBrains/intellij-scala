package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody




import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2

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
      Pattern2.parse(builder)
    } else {
      builder error "pattern expected"
      pattern2sMarker.rollbackTo
      someMarker.rollbackTo
      return false
    }

    while (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
      ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)

      if (BNF.firstPattern2.contains(builder.getTokenType)) {
        Pattern2.parse(builder)
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
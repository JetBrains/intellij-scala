package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.bnf._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 18:15:00
* To change this template use File | Settings | File Templates.
*/

/*
 * Patern ::= Pattern1 {'|' Pattern1}
 */

object Pattern {
  def parse(builder: PsiBuilder): boolean = {
    val patternMarker = builder.mark
    if (!Pattern1.parse(builder)) {
      patternMarker.drop
      return false
    }
    while (builder.getTokenText == "|") {
      builder.advanceLexer //Ate |
      if (!Pattern1.parse(builder)) {
        builder error ScalaBundle.message("wrong.pattern", new Array[Object](0))
      }
    }
    patternMarker.done(ScalaElementTypes.PATTERN)
    return true
  }
}
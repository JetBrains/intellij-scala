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
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
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
    var isComposite = false
    while (builder.getTokenText == "|") {
      isComposite = true
      builder.advanceLexer //Ate |
      if (!Pattern1.parse(builder)) {
        builder error ScalaBundle.message("wrong.pattern", new Array[Object](0))
      }
    }
    if (isComposite) patternMarker.done(ScalaElementTypes.PATTERN)
    else patternMarker.drop
    return true
  }
}
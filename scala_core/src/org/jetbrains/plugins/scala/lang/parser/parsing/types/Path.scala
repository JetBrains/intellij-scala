package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import ScalaElementTypes._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._


/** 
* @author Alexander Podkhalyuzin
*/

/*
 * Path ::= StableId
 *        | [id '.'] 'this'
 */

object Path extends ParserNode with ScalaTokenTypes {
  def parse(builder: PsiBuilder, element: ScalaElementType): Boolean = {
    if (lookAhead(builder, tIDENTIFIER, tDOT, kTHIS)) {
      val thisMarker = builder.mark
      val refMarker = builder.mark
      builder.advanceLexer
      refMarker.done(REFERENCE)
      builder.advanceLexer
      builder.advanceLexer
      val nm = thisMarker.precede
      thisMarker.done(THIS_REFERENCE)
      if (lookAhead(builder, tDOT, tIDENTIFIER)) {
        builder.advanceLexer
        StableId parseQualId(builder, nm, element, false)
      } else {
        nm.drop()
      }
      true
    } else if (builder.getTokenType == kTHIS &&
               !lookAhead(builder, kTHIS, tDOT)) {
      val thisMarker = builder.mark
      builder.advanceLexer
      thisMarker.done(THIS_REFERENCE)
      true
    } else {
      StableId parse(builder, element)
    }
  }
}
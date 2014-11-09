package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder


/**
* @author Alexander Podkhalyuzin
*/

/*
 * Path ::= StableId
 *        | [id '.'] 'this'
 */

object Path extends ParserNode with ScalaTokenTypes {
  def parse(builder: ScalaPsiBuilder, element: IElementType): Boolean = {
    if (lookAhead(builder, tIDENTIFIER, tDOT, kTHIS)) {
      val thisMarker = builder.mark
      val refMarker = builder.mark
      builder.advanceLexer()
      refMarker.done(REFERENCE)
      builder.getTokenType
      builder.advanceLexer()
      builder.getTokenType
      builder.advanceLexer()
      val nm = thisMarker.precede
      thisMarker.done(THIS_REFERENCE)
      if (lookAhead(builder, tDOT, tIDENTIFIER)) {
        builder.advanceLexer()
        StableId parseQualId(builder, nm, element, false)
      } else {
        nm.drop()
      }
      true
    } else if (lookAhead(builder, kTHIS, tDOT, kTYPE) ||
                 builder.getTokenType == kTHIS &&
                 !lookAhead(builder, kTHIS, tDOT)) {
      val thisMarker = builder.mark
      builder.advanceLexer()
      thisMarker.done(THIS_REFERENCE)
      true
    } else {
      StableId parse(builder, element)
    }
  }
}
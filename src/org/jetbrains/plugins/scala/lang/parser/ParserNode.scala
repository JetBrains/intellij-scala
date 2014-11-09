package org.jetbrains.plugins.scala
package lang
package parser

/**
* @author ilyas 
*/

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

trait ParserNode extends ScalaTokenTypes {
  def lookAhead(builder: PsiBuilder, elems: IElementType*): Boolean = {
    if (!(elems(0) == builder.getTokenType)) return false
    if (elems.length == 1) return true
    val rb: PsiBuilder.Marker = builder.mark
    builder.advanceLexer()
    var i: Int = 1
    while (!builder.eof && i < elems.length && (elems(i) == builder.getTokenType)) {
      builder.advanceLexer()
      i += 1
    }
    rb.rollbackTo()
    i == elems.length
  }
}
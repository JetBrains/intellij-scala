package org.jetbrains.plugins.scala.lang.editor

import com.intellij.codeInsight.editorActions._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
* User: Alexander.Podkhalyuzin
* Date: 30.05.2008
*/

class ScalaQuoteHandlerEP extends QuoteHandlerEP {
  override def getHandler(): QuoteHandler = {
    return new ScalaQuoteHandler
  }
}
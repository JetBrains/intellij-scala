package org.jetbrains.plugins.scala.structuralSearch.filter

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

class LeafIdentifierFilter extends NodeFilter {
  override def accepts(element: PsiElement): Boolean =
    element.elementType == ScalaTokenTypes.tIDENTIFIER
}


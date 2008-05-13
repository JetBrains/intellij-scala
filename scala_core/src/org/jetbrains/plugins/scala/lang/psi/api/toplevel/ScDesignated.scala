package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi.{PsiNamedElement, PsiElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

trait ScNamedElement extends ScalaPsiElement with PsiNamedElement {
  def name() : String = nameNode match {
    case null => null
    case e => e.getText()
  }

  def nameNode() : PsiElement = {
    def isName = (elementType: IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)
    childSatisfyPredicateForElementType(isName)
  }

  def setName(name: String): PsiElement = this //todo
}
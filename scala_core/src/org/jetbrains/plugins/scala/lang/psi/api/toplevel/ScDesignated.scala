package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi.{PsiNamedElement, PsiElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

trait ScNamedElement extends ScalaPsiElement with PsiNamedElement {

  def name() : String = nameId.getText

  override def getName = name

  def nameId() : PsiElement

  override def getTextOffset() = nameId.getTextRange.getStartOffset

  override def setName(name: String): PsiElement = this //todo
}
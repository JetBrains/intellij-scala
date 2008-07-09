package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import psi.ScalaPsiElement
import com.intellij.psi.{PsiNamedElement, PsiElement}

trait ScNamedElement extends ScalaPsiElement with PsiNamedElement {

  def name() : String = nameId.getText

  override def getName = name

  def nameId() : PsiElement

  override def getTextOffset() = nameId.getTextRange.getStartOffset

  override def setName(name: String): PsiElement = this //todo
}
package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi.{PsiNamedElement, PsiElement}
import impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import psi.ScalaPsiElement
import typedef.ScTypeDefinition

trait ScNamedElement extends ScalaPsiElement with PsiNamedElement {

  def name() : String = nameId.getText

  override def getName = name

  def nameId() : PsiElement

  override def setName(name: String): PsiElement = {
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id, ScalaPsiElementFactory.createIdentifier(name, getManager))
    return this
  }
}
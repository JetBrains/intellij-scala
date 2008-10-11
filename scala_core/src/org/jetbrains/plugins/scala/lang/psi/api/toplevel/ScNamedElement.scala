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
    val oldName = id.getText
    val parent = id.getTreeParent
    def shortName(s: String): String = {
      if (!s.endsWith(".scala")) return null
      else return s.substring(0, s.length - 6)
    }
    if (id.getText == shortName(id.getPsi.getContainingFile.getName)) {
      var par = parent.getPsi
      while (par != null && !par.isInstanceOf[ScTypeDefinition]) par = par.getParent
      if (par != null) par.getParent match {
        case x: ScalaFile => x.setName(name + ".scala")
        case _ =>
      }
    }
    parent.replaceChild(id, ScalaPsiElementFactory.createIdentifier(name, getManager))
    return this
  }
}
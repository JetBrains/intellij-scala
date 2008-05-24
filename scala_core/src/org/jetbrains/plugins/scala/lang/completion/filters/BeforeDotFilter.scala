package org.jetbrains.plugins.scala.lang.completion.filters

import com.intellij.psi.tree.IElementType
import com.intellij.psi.PsiElement
import com.intellij.psi.filters.position.LeftNeighbour

class BeforeDotFilter(elems : IElementType*) extends LeftNeighbour {
  override def isAcceptable (element: java.lang.Object, context: PsiElement): Boolean =
    element match {
      case psi : PsiElement => elems contains psi.getNode.getElementType
      case _ => false
    }
}
package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScMarkerOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiElementImpl}

class ScEndImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnd {
  override def getName: String = endingElementDesignator.getText

  override def setName(name: String): PsiElement = {
    endingElementDesignator.replace(ScalaPsiElementFactory.createIdentifier(name).getPsi)
  }

  override def toString: String = "End: " + getName

  override def endingElementDesignator: PsiElement = getLastChild

  override def owner: Option[ScMarkerOwner] = this.parentsInFile.findByType[ScMarkerOwner]

  override def beginMarker: Option[PsiElement] = owner.map(_.beginMarker)

  override def marker: PsiElement = getFirstChild
}

package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiElementImpl}

class ScEndImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnd {
  override def getName: String = endingElementDesignator.getText

  override def setName(name: String): PsiElement = {
    endingElementDesignator.replace(ScalaPsiElementFactory.createIdentifier(name).getPsi)
  }

  override def keyword: PsiElement = getFirstChild

  override def endingElementDesignator: PsiElement = getLastChild

  override def begin: Option[ScBegin] = this.parentsInFile.findByType[ScBegin]

  override def toString: String = "End: " + getName
}

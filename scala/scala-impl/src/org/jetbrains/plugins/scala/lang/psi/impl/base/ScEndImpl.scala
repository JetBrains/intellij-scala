package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.IndirectPsiReference
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiElementImpl}

class ScEndImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnd with IndirectPsiReference {
  override def begin: Option[ScBegin] = this.parentsInFile.findByType[ScBegin]

  override def keyword: PsiElement = getFirstChild

  override def tag: PsiElement = getLastChild

  override def getName: String = tag.getText

  override def setName(name: String): PsiElement = {
    tag.replace(ScalaPsiElementFactory.createIdentifier(name).getPsi)
  }

  override def getRangeInElement: TextRange = tag.getTextRangeInParent

  /* Implement a non-highlighted reference to enable Rename and Find Usages. */
  override protected def finalTarget: Option[PsiElement] = if (tag.isIdentifier) begin else None

  override def toString: String = "End: " + getName
}

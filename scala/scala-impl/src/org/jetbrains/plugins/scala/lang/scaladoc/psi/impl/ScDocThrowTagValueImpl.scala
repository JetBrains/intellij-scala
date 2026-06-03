package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocReferenceLink, ScDocThrowTagValue}

final class ScDocThrowTagValueImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocThrowTagValue {
  override def referenceLink: ScDocReferenceLink = findChild[ScDocReferenceLink].get

  override def getName: String = getText

  override def isSoft: Boolean = false

  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] =
    query.multiResolveScala(incomplete)

  override def getElement: PsiElement = query.getElement

  override def getRangeInElement: TextRange = query.getRangeInElement

  override def resolve(): PsiElement = query.resolve()

  override def getCanonicalText: String = query.getCanonicalText

  override def handleElementRename(newElementName: String): PsiElement = query.handleElementRename(newElementName)

  override def bindToElement(element: PsiElement): PsiElement = query.bindToElement(element)

  override def isReferenceTo(element: PsiElement): Boolean = query.isReferenceTo(element)
}
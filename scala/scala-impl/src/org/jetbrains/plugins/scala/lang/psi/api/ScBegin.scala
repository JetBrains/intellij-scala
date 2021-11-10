package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

trait ScBegin extends ScalaPsiElement {
  /** @return the definition keyword (such as "class") */
  final def keyword: PsiElement = findFirstChildByType(keywordTokenType).get

  /** @return the first named element (for navigation from an end marker) */
  def tag: Option[ScNamedElement] = this.elements.findByType[ScNamedElement]

  protected def keywordTokenType: IElementType

  /** @return the corresponding ScEnd element, if present */
  def end: Option[ScEnd] = endParent.flatMap(_.lastChild).filterByType[ScEnd]

  /** @return an element that contains ScEnd, if present */
  protected def endParent: Option[PsiElement] = Some(this)
}

object ScBegin {
  /** @return begin and end keywords */
  def unapply(begin: ScBegin): Option[(PsiElement, Option[PsiElement])] = Some((begin.keyword, begin.end.map(_.keyword)))
}
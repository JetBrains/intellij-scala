package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd

trait ScBegin extends ScalaPsiElement {
  /**
   * @return the definition keyword (such as "class")
   */
  final def marker: PsiElement = findFirstChildByType(markerElementType).get

  protected def markerElementType: IElementType

  /**
   * @return the corresponding ScEnd element, if present
   */
  def end: Option[ScEnd] = endParent.flatMap(_.lastChild).filterByType[ScEnd]

  /**
   * @return an element that contains ScEnd, if present
   */
  protected def endParent: Option[PsiElement] = Some(this)
}

object ScBegin {
  def unapply(begin: ScBegin): Option[(PsiElement, Option[PsiElement])] = Some((begin.marker, begin.end.map(_.marker)))
}
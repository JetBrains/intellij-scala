package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.{ScMarkerOwner, ScalaPsiElement}

trait ScEnd extends ScalaPsiElement with PsiNamedElement {
  /**
   * @return the token that designates which element is ended by this end-element
   */
  def endingElementDesignator: PsiElement

  def owner: Option[ScMarkerOwner]

  def beginMarker: Option[PsiElement]

  def marker: PsiElement
}

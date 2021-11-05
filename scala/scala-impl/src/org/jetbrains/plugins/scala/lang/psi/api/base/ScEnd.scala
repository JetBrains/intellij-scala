package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.{ScBegin, ScalaPsiElement}

trait ScEnd extends ScalaPsiElement with PsiNamedElement {
  /**
   * @return the "end" keyword
   */
  def keyword: PsiElement

  /**
   * @return the token that designates which element is ended by this end-element
   */
  def endingElementDesignator: PsiElement

  /**
   * @return a definition to which the "end" keyword belongs
   */
  def begin: Option[ScBegin]
}

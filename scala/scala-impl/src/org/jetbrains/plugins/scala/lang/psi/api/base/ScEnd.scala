package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.{ScBegin, ScalaPsiElement}

trait ScEnd extends ScalaPsiElement with PsiNamedElement {
  /** @return the ScBegin element to which this ScEnd element belongs */
  def begin: Option[ScBegin]

  /** @return the "end" keyword */
  def keyword: PsiElement

  /** @return the token that designates which element is ended by this end-element */
  def tag: PsiElement
}

object ScEnd {
  def unapply(end: ScEnd): Option[(Option[ScBegin], ScEnd)] = Some((end.begin, end))
}

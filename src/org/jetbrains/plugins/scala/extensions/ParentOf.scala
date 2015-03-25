package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

/**
 * @author Nikolay.Tropin
 */
object ParentOf {
  def unapplySeq(elem: PsiElement): Option[Seq[PsiElement]] = Option(elem).map(e => e.children.toSeq)
}

object withFirstChild {
  def unapply(elem: PsiElement): Option[(PsiElement, PsiElement)] = elem.firstChild.map(c => (elem, c))
}

object withLastChild {
  def unapply(elem: PsiElement): Option[(PsiElement, PsiElement)] = elem.lastChild.map(c => (elem, c))
}
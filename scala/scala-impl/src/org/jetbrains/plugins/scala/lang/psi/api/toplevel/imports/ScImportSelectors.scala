package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package imports

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScImportSelectors extends ScalaPsiElement {
  def selectors: Seq[ScImportSelector]

  def hasWildcard : Boolean

  def wildcardElement: Option[PsiElement]
}

object ScImportSelectors {
  def unapplySeq(importSelectors: ScImportSelectors): Some[Seq[ScImportSelector]] =
    Some(importSelectors.selectors)
}
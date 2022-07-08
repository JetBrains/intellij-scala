package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package imports

import com.intellij.psi.PsiElement

trait ScImportSelectors extends ScalaPsiElement {
  def selectors: Seq[ScImportSelector]

  def hasWildcard : Boolean

  def wildcardElement: Option[PsiElement]
}

object ScImportSelectors {
  def unapplySeq(importSelectors: ScImportSelectors): Some[Seq[ScImportSelector]] =
    Some(importSelectors.selectors)
}
package org.jetbrains.plugins.scala.lang.psi.api.base.types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScContextBound extends ScalaPsiElement {
  /**
   * @return Actual bound type element
   */
  def typeElement: ScTypeElement

  /**
   * @return Optional `as` name for 3.6+ style context bounds
   */
  def nameId: Option[PsiElement]

  def name: Option[String] = nameId.map(_.getText)
}

object ScContextBound {
  object Named {
    def unapply(bound: ScContextBound): Option[(ScTypeElement, String)] =
      bound.name.map(bound.typeElement -> _)
  }
}

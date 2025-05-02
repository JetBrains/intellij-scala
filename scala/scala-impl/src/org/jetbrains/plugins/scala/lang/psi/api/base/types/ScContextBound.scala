package org.jetbrains.plugins.scala.lang.psi.api.base.types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

trait ScContextBound extends ScalaPsiElement with ScNamedElement {
  /**
   * @return Actual bound type element
   */
  def typeElement: ScTypeElement

  /**
   * @return Optional `as` name for 3.6+ style context bounds
   */
  def nameIdOpt: Option[PsiElement]

  def nameOpt: Option[String] = nameIdOpt.map(_.getText)

  override def nameId: PsiElement = nameIdOpt.orNull
}

object ScContextBound {
  object Named {
    def unapply(bound: ScContextBound): Option[(ScTypeElement, String)] =
      bound.nameOpt.map(bound.typeElement -> _)
  }
}

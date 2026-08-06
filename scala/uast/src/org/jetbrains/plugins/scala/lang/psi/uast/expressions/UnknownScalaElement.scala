package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUElement
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement

/**
 * A last resort to represent unhandled [[ScalaPsiElement]].
 */
final class UnknownScalaElement(
  override protected val scElement: ScalaPsiElement,
  override protected val parent: LazyUElement
) extends ScUElement {
  override type PsiFacade = ScalaPsiElement

  override def asLogString(): String =
    "[!] " + this.getClass.getSimpleName + s" ($getSourcePsi)"
}

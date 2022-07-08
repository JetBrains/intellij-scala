package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

trait ScCompoundTypeElement extends ScTypeElement {
  override protected val typeName = "CompoundType"

  def components : Seq[ScTypeElement] = findChildren[ScTypeElement]
  def refinement: Option[ScRefinement] = findChild[ScRefinement]
}

object ScCompoundTypeElement {
  def unapply(cte: ScCompoundTypeElement): Option[(Seq[ScTypeElement], Option[ScRefinement])] = Option(cte.components, cte.refinement)
}


package org.jetbrains.plugins.scala.lang.psi.api.base
package types

trait ScCompoundTypeElement extends ScTypeElement {
  override protected val typeName = "CompoundType"

  def components : Seq[ScTypeElement] = findChildren[ScTypeElement]
  def refinement: Option[ScRefinement] = findChild[ScRefinement]
}

object ScCompoundTypeElement {
  def unapply(cte: ScCompoundTypeElement): Some[(Seq[ScTypeElement], Option[ScRefinement])] = Some((cte.components, cte.refinement))
}


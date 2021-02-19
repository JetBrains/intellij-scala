package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._



/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScCompoundTypeElementBase extends ScTypeElementBase { this: ScCompoundTypeElement =>
  override protected val typeName = "CompoundType"

  def components : Seq[ScTypeElement] = findChildren[ScTypeElement]
  def refinement: Option[ScRefinement] = findChild[ScRefinement]
  def refinements: Seq[ScRefinement] = findChildren[ScRefinement]
}

abstract class ScCompoundTypeElementCompanion {
  def unapply(cte: ScCompoundTypeElement): Option[(Seq[ScTypeElement], Option[ScRefinement])] = Option(cte.components, cte.refinement)
}

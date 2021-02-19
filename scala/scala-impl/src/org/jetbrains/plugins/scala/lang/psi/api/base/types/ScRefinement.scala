package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScTypeAlias}

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScRefinementBase extends ScalaPsiElementBase { this: ScRefinement =>
  def holders : Seq[ScDeclaredElementsHolder] = findChildren[ScDeclaredElementsHolder]
  def types : Seq[ScTypeAlias] = findChildren[ScTypeAlias]
}
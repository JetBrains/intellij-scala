package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScTypeAlias}

trait ScRefinement extends ScalaPsiElement {
  def holders : Seq[ScDeclaredElementsHolder] = findChildren[ScDeclaredElementsHolder]
  def types : Seq[ScTypeAlias] = findChildren[ScTypeAlias]
}
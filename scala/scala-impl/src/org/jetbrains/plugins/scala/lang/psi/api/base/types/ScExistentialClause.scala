package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration

trait ScExistentialClause extends ScalaPsiElement {
  def declarations : Seq[ScDeclaration] = findChildren[ScDeclaration]
}
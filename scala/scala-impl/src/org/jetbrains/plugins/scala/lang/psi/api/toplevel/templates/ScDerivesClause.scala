package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package templates

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDerivesClauseOwner

trait ScDerivesClause extends ScalaPsiElement {
  def derivedReferences: Seq[ScReference]

  def owner: ScDerivesClauseOwner
}

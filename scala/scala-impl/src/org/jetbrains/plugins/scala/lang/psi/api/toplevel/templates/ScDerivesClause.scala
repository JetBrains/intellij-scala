package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDerivesClauseOwner

trait ScDerivesClause extends ScalaPsiElement {
  def derivedReferences: Seq[ScReference]

  def owner: ScDerivesClauseOwner
}

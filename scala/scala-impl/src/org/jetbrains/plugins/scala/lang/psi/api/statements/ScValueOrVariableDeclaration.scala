package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

trait ScValueOrVariableDeclaration extends ScValueOrVariable with ScTypedDeclaration {
  def getIdList: ScIdList

  override def declaredElements: Seq[ScTypedDefinition]

  override def isAbstract: Boolean = true
}

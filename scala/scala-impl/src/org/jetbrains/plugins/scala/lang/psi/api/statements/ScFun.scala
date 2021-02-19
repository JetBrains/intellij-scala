package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypeParametersOwnerBase}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFunBase extends ScTypeParametersOwnerBase { this: ScFun =>
  def retType: ScType

  def paramClauses: Seq[Seq[Parameter]]
}
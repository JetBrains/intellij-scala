package org.jetbrains.plugins.scala.autoImport

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

final class GlobalImplicitConversion(owner: ScTypedDefinition,
                                     pathToOwner: String,
                                     val function: ScFunction)
  extends GlobalMember(owner, pathToOwner, function)

object GlobalImplicitConversion {

  def apply(owner: ScTypedDefinition, pathToOwner: String, function: ScFunction): GlobalImplicitConversion =
    new GlobalImplicitConversion(owner, pathToOwner, function)

  def apply(owner: ScObject, function: ScFunction): GlobalImplicitConversion =
    GlobalImplicitConversion(owner, owner.qualifiedName, function)
}
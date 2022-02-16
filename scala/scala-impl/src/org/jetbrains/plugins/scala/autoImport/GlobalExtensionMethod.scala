package org.jetbrains.plugins.scala.autoImport
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

final case class GlobalExtensionMethod(override val owner: ScTypedDefinition,
                                       override val pathToOwner: String,
                                       function: ScFunction)
  extends GlobalMember(owner, pathToOwner, function)

package org.jetbrains.plugins.scala.autoImport

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

case class GlobalTypeAlias(override val owner: ScTypedDefinition,
                           override val pathToOwner: String,
                           override val member: ScTypeAlias)
  extends GlobalMember(owner, pathToOwner, member)

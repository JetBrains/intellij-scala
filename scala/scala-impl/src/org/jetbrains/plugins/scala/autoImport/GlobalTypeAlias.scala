package org.jetbrains.plugins.scala.autoImport

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

case class GlobalTypeAlias(override val owner: GlobalMemberOwner,
                           override val pathToOwner: String,
                           override val member: ScTypeAlias)
  extends GlobalMember(owner, pathToOwner, member)

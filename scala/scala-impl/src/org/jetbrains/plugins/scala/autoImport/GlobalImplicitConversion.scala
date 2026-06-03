package org.jetbrains.plugins.scala.autoImport

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

final case class GlobalImplicitConversion(override val owner: GlobalMemberOwner,
                                          override val pathToOwner: String,
                                          function: ScFunction)
  extends GlobalMember(owner, pathToOwner, function)

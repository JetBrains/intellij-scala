package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase

trait ScEnum extends ScConstructorOwner with ScDerivesClauseOwner {
  def cases: Seq[ScEnumCase]
}

package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

trait ScEnumClassCase extends ScEnumCase with ScClass {
  /**
   * @return True if this case references enum type parameters in its super constructor/parent types.
   */
  def mentionsEnumTypeParameters: Boolean
}
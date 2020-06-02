package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.base.ScGivenSignature
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven

trait ScGivenImpl extends ScGiven  {
  override def signature: Option[ScGivenSignature] = ???
}

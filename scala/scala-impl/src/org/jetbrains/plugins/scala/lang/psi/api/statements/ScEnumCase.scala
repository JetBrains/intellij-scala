package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScEnum}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScEnumCase extends ScConstructorOwner {
  def enumParent: Option[ScEnum]
}

object ScEnumCase {
  object SingletonCase {
    def unapply(cse: ScEnumCase): Option[(String, Seq[ScType])] =
      Option.when(cse.constructor.isEmpty)((cse.name, cse.superTypes))
  }
}

package org.jetbrains.plugins.scala.lang.psi
package types
package result

trait Typeable {
  def `type`(): TypeResult
}

object Typeable {

  def unapply(typeable: Typeable): Option[ScType] = typeable match {
    case null => None
    case _ => typeable.`type`().toOption
  }
}

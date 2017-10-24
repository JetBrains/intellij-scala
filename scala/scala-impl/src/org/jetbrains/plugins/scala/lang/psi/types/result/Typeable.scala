package org.jetbrains.plugins.scala.lang.psi
package types
package result

trait Typeable {
  def `type`(): TypeResult[ScType]
}

object Typeable {

  def unapply(typeable: Typeable): Option[ScType] = Option(typeable)
    .flatMap(_.`type`().toOption)
}

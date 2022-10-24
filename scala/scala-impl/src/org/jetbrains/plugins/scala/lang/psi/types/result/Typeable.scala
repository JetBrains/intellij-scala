package org.jetbrains.plugins.scala.lang.psi.types.result

import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait Typeable {
  def `type`(): TypeResult
}

object Typeable {
  def unapply(typeable: Typeable): Option[ScType] = typeable.`type`().toOption
}

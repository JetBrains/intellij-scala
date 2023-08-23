package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.types.Signature

private[psi] trait SignatureSink[T <: Signature] {
  def put(signature: T): Unit
}

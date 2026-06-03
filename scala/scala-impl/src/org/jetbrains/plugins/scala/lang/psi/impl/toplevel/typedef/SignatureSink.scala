package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.types.Signature

private[typedef] trait SignatureSink[T <: Signature] {
  def put(signature: T): Unit
}

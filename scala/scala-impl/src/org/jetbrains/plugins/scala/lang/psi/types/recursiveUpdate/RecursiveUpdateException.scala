package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.ScalaBundle

class RecursiveUpdateException extends Exception {
  override def getMessage: String = ScalaBundle.message("type.mismatch.after.update.method")
}
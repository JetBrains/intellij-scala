package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

class RecursiveUpdateException extends Exception {
  override def getMessage: String = "Type mismatch after update method"
}
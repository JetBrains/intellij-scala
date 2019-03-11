package org.jetbrains.plugins.scala.findUsages.compilerReferences

trait TransactionGuard[State] {
  def inTransaction[T](body: State => T): T
}

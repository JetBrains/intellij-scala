package org.jetbrains.plugins.scala.findUsages.compilerReferences

trait TransactionManager[State] {
  def inTransaction[T](body: State => T): T
}

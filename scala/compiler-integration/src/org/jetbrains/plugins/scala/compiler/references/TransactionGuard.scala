package org.jetbrains.plugins.scala.compiler.references

trait TransactionGuard[State] {
  def inTransaction[T](body: State => T): T
}

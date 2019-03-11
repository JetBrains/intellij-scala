package org.jetbrains.plugins.scala.findUsages.compilerReferences
package compilation

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaCompilerReferenceService.CompilerIndicesState

private[compilerReferences] trait CompilationWatcher[M <: CompilerMode] {
  protected type State = CompilerIndicesState

  def project: Project
  def compilerMode: M
  def start(): Unit
  def transactionGuard: TransactionGuard[State]

  protected final def isEnabledFor(mode: CompilerMode): Boolean = mode == compilerMode

  /** Execute `body` transactionally (i.e. with guaranted exclusive access to state) */
  protected final def transaction[T](body: State => T): T = transactionGuard.inTransaction(body)

  /** Execute `body` in transaction under the condition that current compiler mode is [[M]] */
  protected final def processEventInTransaction(body: CompilerIndicesEventPublisher => Unit): Unit =
    transaction { case (mode, publisher) => if (isEnabledFor(mode)) body(publisher) }
}

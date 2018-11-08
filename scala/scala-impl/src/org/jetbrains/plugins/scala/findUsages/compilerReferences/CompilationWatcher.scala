package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.openapi.project.Project

private trait CompilationWatcher[M <: CompilerMode] {
  def project: Project
  def compilerMode: M
  def start(): Unit

  protected def currentCompilerMode: () => CompilerMode

  protected final def isEnabled: Boolean = currentCompilerMode() == compilerMode

  protected final val eventPublisher: CompilerIndicesCompilationWatcher =
    project.getMessageBus.syncPublisher(CompilerIndicesCompilationWatcher.topic)
}

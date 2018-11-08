package org.jetbrains.plugins.scala.findUsages.compilerReferences

import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo

/**
  * High level (i.e. as opposed to [[SbtCompilationListener]]
  * or custom handler for [[org.jetbrains.plugin.scala.compilerReferences.ScalaCompilerReferenceIndexBuilder]]) compilation listening component.
  * NOTICE: indexing is separated from compilation, hence separate `onCompilationStart` and `startIndexing` methdods.
  */
private trait CompilerIndicesEventPublisher {
  def compilerModeChanged(mode: CompilerMode): Unit                         = ()
  def onError(): Unit                                                       = ()
  def onCompilationStart(): Unit                                            = ()
  def onCompilationFinish(): Unit                                           = ()
  def startIndexing(isCleanBuild: Boolean): Unit                            = ()
  def processCompilationInfo(info: CompilationInfo, offline: Boolean): Unit = ()
  def finishIndexing(): Unit                                                = ()
}

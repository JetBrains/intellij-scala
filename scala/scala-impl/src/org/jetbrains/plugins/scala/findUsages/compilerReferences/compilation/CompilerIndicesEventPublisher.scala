package org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation

import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo

/**
  * High level (i.e. as opposed to [[SbtCompilationListener]]
  * or custom handler for [[org.jetbrains.plugin.scala.compilerReferences.ScalaCompilerReferenceIndexBuilder]]) compilation listening component.
  * NOTICE: indexing is separated from compilation, hence separate `onCompilationStart` and `startIndexing` methdods.
  */
private[compilerReferences] trait CompilerIndicesEventPublisher {
  def onCompilerModeChange(mode: CompilerMode): Unit                        = ()
  def onError(message: String, cause: Option[Throwable] = None): Unit       = ()
  def onCompilationStart(): Unit                                            = ()
  def onCompilationFinish(success: Boolean): Unit                           = ()
  def startIndexing(isCleanBuild: Boolean): Unit                            = ()
  def processCompilationInfo(info: CompilationInfo, offline: Boolean): Unit = ()
  def finishIndexing(): Unit                                                = ()
}

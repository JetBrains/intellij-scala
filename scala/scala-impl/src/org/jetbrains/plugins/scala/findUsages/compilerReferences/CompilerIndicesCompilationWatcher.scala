package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.util.messages.Topic
import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo


/**
  * High level (i.e. as opposed to [[SbtCompilationListener]]
  * or custom handler for [[org.jetbrains.plugin.scala.compilerReferences.ScalaCompilerReferenceIndexBuilder]]) compilation listening component.
  * NOTICE: indexing is separated from compilation, hence separate `onCompilationStart` and `startIndexing` methdods.
  */
private trait CompilerIndicesCompilationWatcher {
  def onError(): Unit                                                               = ()
  def onCompilationFailure(): Unit                                                  = ()
  def onCompilationStart(): Unit                                                    = ()
  def startIndexing(isCleanBuild: Boolean): Unit                                    = ()
  def processCompilationInfo(info: CompilationInfo, offline: Boolean = false): Unit = ()
  def finishIndexing(timestamp: Long): Unit                                         = ()
}

private object CompilerIndicesCompilationWatcher {
  val topic: Topic[CompilerIndicesCompilationWatcher] =
    Topic.create("external compilation events", classOf[CompilerIndicesCompilationWatcher])
}

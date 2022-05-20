package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.util.messages.Topic

import java.util.EventListener

trait CompilerReferenceServiceStatusListener extends EventListener {
  def onIndexingPhaseStarted(): Unit                       = ()
  def onCompilationInfoIndexed(modules: Set[String]): Unit = ()
  def onIndexingPhaseFinished(success:  Boolean): Unit     = ()
}

object CompilerReferenceServiceStatusListener {
  val topic: Topic[CompilerReferenceServiceStatusListener] =
    Topic.create[CompilerReferenceServiceStatusListener](
      "compiler reference index build status",
      classOf[CompilerReferenceServiceStatusListener]
    )
}

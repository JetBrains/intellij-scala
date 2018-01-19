package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util.EventListener

trait CompilerReferenceIndexingStatusListener extends EventListener {
  def beforeIndexingStarted(): Unit                                   = ()
  def onIndexingFinished(affectedModuleNames: Iterable[String]): Unit = ()
}

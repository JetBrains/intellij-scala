package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util.EventListener

trait CompilerReferenceIndexingStatusListener extends EventListener {
  def beforeIndexingStarted(): Unit = ()
  def modulesUpToDate(moduleNames: Iterable[String]): Unit       = ()
  def onIndexingFinished(failure:  Option[IndexerFailure]): Unit = ()
}

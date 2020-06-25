package org.jetbrains.plugins.scala.caches

import scala.collection.mutable.ArrayBuffer

class CleanupScheduler {
  private val actions: ArrayBuffer[() => Unit] = ArrayBuffer.empty

  def subscribe(action: () => Unit): Unit = actions += action

  def fireCleanup(): Unit = actions.foreach(_.apply())

}

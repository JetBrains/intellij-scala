package org.jetbrains.plugins.scala.caches

import java.util.concurrent.ConcurrentLinkedQueue

class CleanupScheduler {
  private val actions = new ConcurrentLinkedQueue[() => Unit]

  def subscribe(action: () => Unit): Unit = actions.add(action)

  def fireCleanup(): Unit = actions.forEach(_.apply())
}

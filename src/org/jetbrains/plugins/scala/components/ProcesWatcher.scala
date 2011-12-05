package org.jetbrains.plugins.scala
package components

import actors.Actor._

/**
 * Pavel Fatin
 */

class ProcesWatcher {
  def watch(process: Process) {
    WatcherActor ! process
  }

  def running: Boolean = {
    WatcherActor !? (100, IsRunning) match {
      case Some(b: Boolean) => b
      case None => false
    }
  }

  def stop() {
    WatcherActor ! Exit
  }

  private val WatcherActor = actor {
    var running = false

    val watcher = self

    loop {
      react {
        case p: Process =>
          running = true
          actor {
            p.waitFor()
            watcher ! Stopped
          }
        case IsRunning => reply(running)
        case Stopped => running = false
        case Exit => exit()
      }
    }
  }

  private case object IsRunning

  private case object Stopped

  private case object Exit
}

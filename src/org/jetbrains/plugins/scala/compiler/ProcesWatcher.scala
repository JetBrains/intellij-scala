package org.jetbrains.plugins.scala
package compiler

import actors.Actor._
import java.io.{IOException, InputStream, InputStreamReader}
import ProcesWatcher._

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

  def errors(): Seq[String] = {
    WatcherActor !? (100, GetErrors) match {
      case Some(messages: Seq[String]) => messages
      case None => Seq.empty
    }
  }

  private val WatcherActor = actor {
    var running = false

    var messages = Vector[String]()

    val watcher = self

    loop {
      react {
        case p: Process =>
          running = true
          actor {
            p.waitFor()
            watcher ! Stopped
          }
          actor {
            read(p.getErrorStream) { message =>
              watcher ! Error(message)
            }
          }
          actor {
            var exception = false

            read(p.getInputStream) { message =>
              if (exception || ExceptionPattern.matcher(message).find()) {
                exception = true
                watcher ! Error(message)
              }
            }
          }

        case Error(message) =>
          messages :+= message
        case Stopped =>
          running = false

        case GetErrors =>
          reply(messages)
          messages = Vector.empty
        case IsRunning =>
          reply(running)
        case Exit =>
          exit()
      }
    }
  }
}

object ProcesWatcher {
  private val ExceptionPattern = "\\.[A-Za-z]+(?:Error|Exception):".r.pattern

  private def read(stream: InputStream)(callback: String => Unit) {
    val buffer = new Array[Char](1024)
    val reader = new InputStreamReader(stream)

    var continue = true

    while(continue) {
      val length = try {
        reader.read(buffer)
      } catch {
        case _: IOException => -1
      }

      if (length == -1) {
        continue = false
      } else {
        val message = new String(buffer, 0, length)
        callback(message)
      }
    }
  }

  private case object IsRunning

  private case object GetErrors

  private case object Stopped

  private case object Exit

  private case class Error(message: String)
}

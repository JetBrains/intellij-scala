package org.jetbrains.plugins.scala
package compiler

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

trait CompilationProcess {
  def run(): Unit

  final def runSync(): Unit = {
    val result = Promise[Unit]()
    addTerminationCallback {
      case Some(error) => result.failure(error)
      case None => result.success(())
    }
    run()
    Await.result(result.future, Duration.Inf)
  }

  def stop(): Unit

  def addTerminationCallback(callback: Option[Throwable] => Unit): Unit
}
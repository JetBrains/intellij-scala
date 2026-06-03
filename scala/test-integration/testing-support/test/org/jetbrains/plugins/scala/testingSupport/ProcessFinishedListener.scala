package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.process.{ProcessEvent, ProcessListener}

import scala.concurrent.{Future, Promise}

final class ProcessFinishedListener extends ProcessListener {

  private val exitCodePromise: Promise[Int] = Promise()
  def exitCodeFuture: Future[Int] = exitCodePromise.future

  override def processTerminated(event: ProcessEvent): Unit =
    exitCodePromise.success(event.getExitCode)
}
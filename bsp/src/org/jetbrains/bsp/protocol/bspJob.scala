package org.jetbrains.bsp.protocol

import org.jetbrains.bsp.BspError

import scala.concurrent.{ExecutionContext, Future}


abstract class BspJob[T] {
  def future: Future[T]
  def cancel(): Unit
}

class NonAggregatingBspJob[T,A](job: BspJob[(T,A)]) extends BspJob[T] {
  override def future: Future[T] = job.future.map(_._1)(ExecutionContext.global)
  override def cancel(): Unit = job.cancel()
}

class FailedBspJob[T](error: BspError) extends BspJob[T] {
  override def future: Future[T] = Future.failed(error)
  override def cancel(): Unit = ()
}



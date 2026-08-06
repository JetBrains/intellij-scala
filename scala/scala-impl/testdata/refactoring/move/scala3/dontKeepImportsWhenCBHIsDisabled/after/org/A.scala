package org

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

object A {

  val b(): B = ???

  def foo(i: Int)(using ctx: ExecutionContext) = ???

  foo(1)
}

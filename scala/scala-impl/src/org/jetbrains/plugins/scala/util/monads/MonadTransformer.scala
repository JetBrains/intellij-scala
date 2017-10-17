package org.jetbrains.plugins.scala.util.monads

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult}

/**
 * @author ilyas
 */

trait MonadTransformer { self : ScalaPsiElement =>

  class MonadLike[+T](opt: Option[T])(implicit msg: String) {
    def flatMap[U <: ScType](f: T => TypeResult[U]): TypeResult[U] = opt match {
      case Some(elem) => f(elem)
      case None =>  Failure(msg, Some(self))
    }
    def map[U <: ScType](f: T => U): TypeResult[U] = opt match {
      case Some(elem) => Success(f(elem), Some(self))
      case None         => Failure(msg, None)
    }
  }

  class SemiMonadLike[+T](opt: Option[T], default: ScType)(implicit msg: String) {
    def flatMap(f: T => TypeResult[ScType]): TypeResult[ScType] = opt match {
      case Some(elem) => f(elem)
      case None       => Success(default, None)
    }
  }

  implicit def DEFAULT_ERROR_MESSAGE = "No element found"

  def wrap[T](opt: Option[T])(implicit msg: String): MonadLike[T] = new MonadLike[T](opt)(msg)

  def wrapWith[T](opt: Option[T], default: ScType)(implicit msg: String): SemiMonadLike[T] = {
    new SemiMonadLike[T](opt, default)(msg)
  }
}

package org.jetbrains.plugins.scala.util.monads

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.{PsiElement}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, Failure, TypeResult}

/**
 * @author ilyas
 */

trait MonadTransformer { self : PsiElement =>

  type MonadLike[+T] = {
    def flatMap[U <: ScType](f: T => TypeResult[U]): TypeResult[U]
    def map[U <: ScType](f: T => U): TypeResult[U]
  }

  implicit val DEFAULT_ERROR_MESSAGE = "No element found"

  implicit def wrap[T](opt: Option[T])(implicit msg: String): MonadLike[T] = new {
    def flatMap[U <: ScType](f: T => TypeResult[U]): TypeResult[U] = opt match {
      case Some(elem) => f(elem)
      case None =>  Failure(msg, Some(self))
    }
    def map[U <: ScType](f: T => U): TypeResult[U] = opt match {
      case s@Some(elem) => Success(f(elem), Some(self))
      case None         => Failure(msg, None)
    }
  }
}
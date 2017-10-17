package org.jetbrains.plugins.scala
package lang
package psi
package types
package result

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * @author ilyas
 */

sealed abstract class TypeResult[+T](implicit val projectContext: ProjectContext) {
  def map[U](f: T => U): TypeResult[U]
  def flatMap[U](f: T => TypeResult[U]): TypeResult[U]
  def filter(f: T => Boolean): TypeResult[T]
  def withFilter(f: T => Boolean): TypeResultWithFilter[T]
  def exists(f: T => Boolean): Boolean = !filter(f).isEmpty
  def foreach[B](f: T => B)
  def get: T
  def isEmpty : Boolean
  def isDefined: Boolean = !isEmpty
  def getOrElse[U >: T](default: => U): U = if (isEmpty) default else this.get
  def toOption: Option[T] = if (isEmpty) None else Some(this.get)

  final def apply(fail: Failure): TypeResult[T] = this

  def getOrNothing(implicit ev: T <:< ScType): ScType = getOrType(Nothing)
  def getOrAny(implicit ev: T <:< ScType): ScType = getOrType(Any)
  def getOrType(default: ScType)(implicit ev: T <:< ScType): ScType = if (isEmpty) default else this.get
}

object TypeResult {
  def fromOption(o: Option[ScType])
                (implicit pc: ProjectContext): TypeResult[ScType] = o match {
    case Some(t) => Success(t, None)
    case None => Failure("", None)
  }

  def Any(implicit project: ProjectContext) = Success(project.stdTypes.Any, None)
  def Nothing(implicit project: ProjectContext) = Success(project.stdTypes.Nothing, None)
}

case class Success[+T](result: T, elem: Option[PsiElement])
                      (implicit pc: ProjectContext) extends TypeResult[T] { self =>
  def flatMap[U](f: (T) => TypeResult[U]): TypeResult[U] = f(result)
  def map[U](f: T => U) = Success(f(result), elem)
  def filter(f: T => Boolean): TypeResult[T] = if (f(result)) Success(result, elem) else Failure("Wrong type", elem)
  def withFilter(f: (T) => Boolean): TypeResultWithFilter[T] = new TypeResultWithFilter[T](this, f)
  def foreach[B](f: T => B): Unit = f(result)
  def get: T = result
  def isEmpty = false
}

class TypeResultWithFilter[+T](self: TypeResult[T], p: T => Boolean) {
  def map[B](f: T => B): TypeResult[B] = self filter p map f
  def foreach[B](f: T => B): Unit = self filter p foreach f
  def flatMap[B](f: T => TypeResult[B]): TypeResult[B] = self filter p flatMap f
  def withFilter(q: T => Boolean): TypeResultWithFilter[T] = new TypeResultWithFilter[T](self, x => p(x) && q(x))
}

case class Failure(cause: String, place: Option[PsiElement])
                  (implicit pc: ProjectContext) extends TypeResult[Nothing] {
  def flatMap[U](f: Nothing => TypeResult[U]): Failure = this
  def map[U](f: Nothing => U): Failure = this
  def foreach[B](f: Nothing => B) {}
  def withFilter(f: (Nothing) => Boolean): TypeResultWithFilter[Nothing] = new TypeResultWithFilter[Nothing](this, f)
  def filter(f: Nothing => Boolean): Failure = this
  def get = throw new NoSuchElementException("Failure.get")
  def isEmpty = true
}
package org.jetbrains.plugins.scala
package lang
package psi
package types
package result

import org.jetbrains.plugins.scala.lang.psi.types.result.Failure.TypeError
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author ilyas
  */
sealed abstract class TypeResult[+T <: ScType] {

  def map[U <: ScType](f: T => U): TypeResult[U]

  def flatMap[U <: ScType](f: T => TypeResult[U]): TypeResult[U]

  def withFilter(f: T => Boolean): TypeResult[T]

  def foreach[B](f: T => B)

  def get: T

  def isEmpty: Boolean

  def getOrNothing: ScType

  def getOrAny: ScType

  final def isDefined: Boolean = !isEmpty

  final def exists(f: T => Boolean): Boolean = withFilter(f).isDefined

  final def getOrElse[U >: T](default: => U): U = if (isEmpty) default else get

  final def toOption: Option[T] = if (isEmpty) None else Some(get)
}

object TypeResult {
  def fromOption(maybeType: Option[ScType])
                (implicit context: ProjectContext): TypeResult[ScType] = maybeType match {
    case Some(scType) => Success(scType)
    case None => Failure("")
  }
}

final case class Success[T <: ScType](private val result: T) extends TypeResult[T] {

  def map[U <: ScType](f: T => U): Success[U] = Success(f(result))

  def flatMap[U <: ScType](f: T => TypeResult[U]): TypeResult[U] = f(result)

  def withFilter(f: T => Boolean): TypeResult[T] =
    if (f(result)) this else Failure("Wrong type")(result.projectContext)

  def foreach[B](f: T => B): Unit = f(result)

  def get: T = result

  def isEmpty: Boolean = false

  def getOrNothing: T = result

  def getOrAny: T = result
}

final class Failure private(private val error: TypeError) extends TypeResult[Nothing] {

  def map[U <: ScType](f: Nothing => U): this.type = this

  def flatMap[U <: ScType](f: Nothing => TypeResult[U]): this.type = this

  def withFilter(f: Nothing => Boolean): this.type = this

  def foreach[B](f: Nothing => B): Unit = {}

  def get: Nothing = throw new NoSuchElementException("Failure.get")

  def isEmpty: Boolean = true

  def getOrNothing: ScType = api.Nothing(error.context)

  def getOrAny: ScType = api.Any(error.context)

  override def equals(other: Any): Boolean = other match {
    case that: Failure => error == that.error
    case _ => false
  }

  override def hashCode(): Int = error.hashCode()

  override def toString = s"Failure(${error.cause})"
}

object Failure {

  case class TypeError(private[result] val cause: String,
                       private[result] val context: ProjectContext)

  def apply(message: String)
           (implicit context: ProjectContext): Failure =
    new Failure(TypeError(message, context))

  def unapply(failure: Failure): Option[String] =
    Some(failure.error.cause)

}

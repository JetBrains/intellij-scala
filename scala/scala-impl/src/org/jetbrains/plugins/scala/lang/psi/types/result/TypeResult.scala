package org.jetbrains.plugins.scala
package lang
package psi
package types
package result

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author ilyas
  */
sealed abstract class TypeResult[+T](implicit val projectContext: ProjectContext) {

  def map[U](f: T => U): TypeResult[U]

  def flatMap[U](f: T => TypeResult[U]): TypeResult[U]

  def withFilter(f: T => Boolean): TypeResult[T]

  def foreach[B](f: T => B)

  def get: T

  def isEmpty: Boolean

  final def isDefined: Boolean = !isEmpty

  final def exists(f: T => Boolean): Boolean = withFilter(f).isDefined

  final def getOrElse[U >: T](default: => U): U = if (isEmpty) default else this.get

  final def toOption: Option[T] = if (isEmpty) None else Some(this.get)
}

object TypeResult {
  def fromOption(o: Option[ScType])
                (implicit pc: ProjectContext): TypeResult[ScType] = o match {
    case Some(t) => Success(t, None)
    case None => Failure("", None)
  }
}

case class Success[+T](result: T, elem: Option[PsiElement])
                      (implicit pc: ProjectContext) extends TypeResult[T] {
  self =>

  def map[U](f: T => U): Success[U] = Success(f(result), elem)

  def flatMap[U](f: T => TypeResult[U]): TypeResult[U] = f(result)

  def withFilter(f: T => Boolean): TypeResult[T] = if (f(result)) Success(result, elem) else Failure("Wrong type", elem)

  def foreach[B](f: T => B): Unit = f(result)

  def get: T = result

  def isEmpty: Boolean = false
}

class Failure private(private val cause: String)
                     (implicit context: ProjectContext) extends TypeResult[Nothing] {

  def map[U](f: Nothing => U): Failure = this

  def flatMap[U](f: Nothing => TypeResult[U]): Failure = this

  def withFilter(f: Nothing => Boolean): Failure = this

  def foreach[B](f: Nothing => B): Unit = {}

  def get: Nothing = throw new NoSuchElementException("Failure.get")

  def isEmpty: Boolean = true

  override def toString: String = s"Failure($cause)"

  override def equals(other: Any): Boolean = other match {
    case Failure(otherCause) => cause == otherCause
    case _ => false
  }

  override def hashCode(): Int = cause.hashCode
}

object Failure {

  def apply(cause: String, place: Option[PsiElement])
           (implicit pc: ProjectContext): Failure =
    new Failure(cause)

  def unapply(failure: Failure): Option[String] =
    Option(failure).map(_.cause)
}
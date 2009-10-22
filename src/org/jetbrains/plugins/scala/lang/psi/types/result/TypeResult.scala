package org.jetbrains.plugins.scala.lang.psi.types.result

import com.intellij.psi.PsiElement

/**
 * @author ilyas
 */

sealed abstract class TypeResult[+T] {
  def map[U](f: T => U): TypeResult[U]
  def flatMap[U](f: T => TypeResult[U]): TypeResult[U]
  def filter(f: T => Boolean): TypeResult[T]
  def foreach[B](f: T => B): Unit
  def get: T
  def isEmpty : Boolean
  def getOrElse[U >: T](default: => U): U = if (isEmpty) default else this.get

  def apply(fail: Failure): TypeResult[T]
  def isCyclic: Boolean
}

case class Success[+T](result: T, elem: Option[PsiElement]) extends TypeResult[T] { self =>
  def flatMap[U](f: (T) => TypeResult[U]) = f(result)
  def map[U](f: T => U) = Success(f(result), elem)
  def filter(f: T => Boolean) = if (f(result)) Success(result, elem) else Failure("Wrong type", elem)
  def foreach[B](f: T => B) {f(result)}
  def get = result
  def isEmpty = false

  def innerFailures: List[Failure] = List()
  def apply(fail: Failure) = new Success(result, elem) {
    override def innerFailures = fail :: self.innerFailures
  }
  def isCyclic = false
}

case class Failure(cause: String, place: Option[PsiElement]) extends TypeResult[Nothing] {
  def flatMap[U](f: Nothing => TypeResult[U]) = this
  def map[U](f: Nothing => U) = this
  def foreach[B](f: Nothing => B) {}
  def filter(f: Nothing => Boolean) = this
  def get = throw new NoSuchElementException("Failure.get")
  def isEmpty = true

  def apply(fail: Failure) = this
  def isCyclic = false
}
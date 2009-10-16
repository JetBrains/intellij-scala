package org.jetbrains.plugins.scala.lang.psi.types.result

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.PsiElement

/**
 * @author ilyas
 */

sealed abstract class TypeResult[+T <: ScType] {
  def map[U <: ScType](f: T => U): TypeResult[U]
  def flatMap[U <: ScType](f: T => TypeResult[U]): TypeResult[U]
  def filter(f: T => Boolean): TypeResult[T]
}

case class Success[+T <: ScType](result: T, elem: Option[PsiElement]) extends TypeResult[T] {
  def flatMap[U <: ScType](f: (T) => TypeResult[U]) = f(result)
  def map[U <: ScType](f: T => U) = Success(f(result), elem)
  def filter(f: T => Boolean) = if (f(result)) Success(result, elem) else Failure("Wrong type", elem)
}

case class Failure(cause: String, place: Option[PsiElement]) extends TypeResult[Nothing] {
  def flatMap[U <: ScType](f: Nothing => TypeResult[U]) = this
  def map[U <: ScType](f: Nothing => U) = this
  def filter(f: Nothing => Boolean) = this
}
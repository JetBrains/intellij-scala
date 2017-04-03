package org.jetbrains.plugins.scala
package lang
package psi
package types
package result

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}

/**
 * @author ilyas
 */

sealed abstract class TypeResult[+T] {
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

  def apply(fail: Failure): TypeResult[T]
  def isCyclic: Boolean
  
  def getOrNothing(implicit ev: T <:< ScType): ScType = getOrType(Nothing)
  def getOrAny(implicit ev: T <:< ScType): ScType = getOrType(Any)
  def getOrType(default: ScType)(implicit ev: T <:< ScType): ScType = if (isEmpty) default else this.get
}

object TypeResult {
  def fromOption(o: Option[ScType]): TypeResult[ScType] = o match {
    case Some(t) => Success(t, None)
    case None => new Failure("", None)
  }

  def ap2[A, B, Z](tr1: TypeResult[A], tr2: TypeResult[B])(f: (A, B) => Z): TypeResult[Z] = for {
    t1 <- tr1
    t2 <- tr2
  } yield f(t1, t2)

  def ap3[A, B, C, Z](tr1: TypeResult[A], tr2: TypeResult[B], tr3: TypeResult[C])(f: (A, B, C) => Z): TypeResult[Z] = for {
    t1 <- tr1
    t2 <- tr2
    t3 <- tr3
  } yield f(t1, t2, t3)

  def sequence[A](trs: Seq[TypeResult[A]]): TypeResult[Seq[A]] = {
    val seed: TypeResult[scala.List[A]] = Success(List[A](), None)
    trs.foldLeft(seed) {
      (result, tr) => result.flatMap(as => tr.map(a => a :: as))
    }.map(_.reverse)
  }

  val Any = Success(api.Any, None)
  val Nothing = Success(api.Nothing, None)
}

case class Success[+T](result: T, elem: Option[PsiElement]) extends TypeResult[T] { self =>
  def flatMap[U](f: (T) => TypeResult[U]): TypeResult[U] = f(result)
  def map[U](f: T => U) = Success(f(result), elem)
  def filter(f: T => Boolean): TypeResult[T] = if (f(result)) Success(result, elem) else Failure("Wrong type", elem)
  def withFilter(f: (T) => Boolean): TypeResultWithFilter[T] = new TypeResultWithFilter[T](this, f)
  def foreach[B](f: T => B): Unit = f(result)
  def get: T = result
  def isEmpty = false

  def innerFailures: List[Failure] = List()
  def apply(fail: Failure) = new Success(result, elem) {
    override def innerFailures: List[Failure] = fail :: self.innerFailures
  }
  def isCyclic = false
}

class TypeResultWithFilter[+T](self: TypeResult[T], p: T => Boolean) {
  def map[B](f: T => B): TypeResult[B] = self filter p map f
  def foreach[B](f: T => B): Unit = self filter p foreach f
  def flatMap[B](f: T => TypeResult[B]): TypeResult[B] = self filter p flatMap f
  def withFilter(q: T => Boolean): TypeResultWithFilter[T] = new TypeResultWithFilter[T](self, x => p(x) && q(x))
}

case class Failure(cause: String, place: Option[PsiElement]) extends TypeResult[Nothing] {
  def flatMap[U](f: Nothing => TypeResult[U]): Failure = this
  def map[U](f: Nothing => U): Failure = this
  def foreach[B](f: Nothing => B) {}
  def withFilter(f: (Nothing) => Boolean): TypeResultWithFilter[Nothing] = new TypeResultWithFilter[Nothing](this, f)
  def filter(f: Nothing => Boolean): Failure = this
  def get = throw new NoSuchElementException("Failure.get")
  def isEmpty = true

  def apply(fail: Failure): Failure = this
  def isCyclic = false
}
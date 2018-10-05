package org.jetbrains.plugins.scala.extensions

/** This type is handy for working with possible null values.
  * It has no overhead of converting to Option, and allows to chain several nullable functions in a safe way
  * using `map` and `collect` methods.
  */
final class NullSafe[+A >: Null] private(val a: A) extends AnyVal {

  import NullSafe.Null

  @inline def notNull: Boolean =
    a != null

  @inline def isNull: Boolean =
    a == null

  @inline def toOption: Option[A] = Option(a)

  @inline def orNull: A = a

  @inline def map[B >: Null](f: A => B): NullSafe[B] =
    if (notNull) NullSafe(f(a)) else Null

  @inline def flatMap[B >: Null](f: A => NullSafe[B]): NullSafe[B] =
    if (notNull) f(a) else Null

  @inline def collect[B >: Null](pf: scala.PartialFunction[A, B]): NullSafe[B] =
    if (notNull) NullSafe(pf.applyOrElse(a, Function.const(null))) else Null

  @inline def getOrElse[B >: A](other: => B): B =
    if (notNull) a else other

  @inline def exists(p: A => Boolean): Boolean =
    notNull && p(a)

  @inline def forall(p: A => Boolean): Boolean =
    isNull || p(a)

  @inline def foreach(f: A => Unit): Unit =
    if (notNull) f(a)

  @inline def withFilter(p: A => Boolean): NullSafe[A] =
    if (notNull && p(a)) this else Null

  @inline def filter(p: A => Boolean): NullSafe[A] =
    withFilter(p)
}

object NullSafe {
  def apply[A >: Null](a: A) = new NullSafe(a)

  val Null: NullSafe[Null] = new NullSafe(null)
}
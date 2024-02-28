package org.jetbrains.plugins.scala.extensions

import org.jetbrains.annotations.Nullable

/** This type is handy for working with possible null values.
  * It has no overhead of converting to Option, and allows to chain several nullable functions in a safe way
  * using `map` and `collect` methods.
  */
final case class NullSafe[+A >: Null] private(@Nullable get: A) extends AnyVal {

  import NullSafe.empty

  @inline def notNull: Boolean = get != null

  @inline def isNull: Boolean = get == null

  @inline def toOption: Option[A] = Option(get)

  @inline def orNull: A = get

  @inline def map[B >: Null](f: A => B): NullSafe[B] =
    if (notNull) NullSafe(f(get))
    else empty

  @inline def flatMap[B >: Null](f: A => NullSafe[B]): NullSafe[B] =
    if (notNull) f(get)
    else empty

  @inline def collect[B >: Null](pf: scala.PartialFunction[A, B]): NullSafe[B] =
    if (notNull) NullSafe(pf.applyOrElse(get, Function.const(null)))
    else empty

  @inline def orElse[B >: A](other: => NullSafe[B]): NullSafe[B] =
    if (notNull) this
    else other

  @inline def getOrElse[B >: A](other: => B): B =
    if (notNull) get
    else other

  @inline def exists(p: A => Boolean): Boolean =
    notNull && p(get)

  @inline def contains[B >: A](element: B): Boolean =
    notNull && get == element

  @inline def forall(p: A => Boolean): Boolean =
    isNull || p(get)

  @inline def foreach(f: A => Unit): Unit =
    if (notNull) f(get)

  @inline def withFilter(p: A => Boolean): NullSafe[A] =
    if (exists(p)) this
    else empty

  @inline def filter(p: A => Boolean): NullSafe[A] =
    withFilter(p)

  @inline def fold[B](ifEmpty: => B)
                     (f: A => B): B =
    if (notNull) f(get)
    else ifEmpty
}

object NullSafe {

  def apply[A >: Null](@Nullable a: A) = new NullSafe(a)

  def empty: NullSafe[Null] = new NullSafe(null)
}
package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.extensions.NullSafe.{Null, constNull}


/** This type is handy for working with possible null values.
  * It has no overhead of converting to Option, and allows to chain several nullable functions in a safe way
  * using `map` and `collect` methods.
  */
class NullSafe[+A >: Null](val a: A) extends AnyVal {

  @inline def notNull: Boolean =
    a != null

  @inline def isNull: Boolean =
    a == null

  @inline def toOption: Option[A] = Option(a)

  @inline def orNull: A = a

  @inline def map[S >: Null](f: A => S): NullSafe[S] =
    if (notNull) NullSafe(f(a)) else Null

  @inline def collect[B >: Null](pf: scala.PartialFunction[A, B]): NullSafe[B] =
    if (notNull) NullSafe(pf.applyOrElse(a, constNull)) else Null

  @inline def getOrElse[B >: A](other: => B): B =
    if (notNull) a else other

  @inline def exists(p: A => Boolean): Boolean =
    if (notNull) p(a) else false

  @inline def forall(p: A => Boolean): Boolean =
    if (notNull) p(a) else true

  @inline def foreach(f: A => Unit): Unit =
    if (notNull) f(a)

  @inline def filter(p: A => Boolean): NullSafe[A] =
    if (notNull && p(a)) NullSafe(a) else Null
}

object NullSafe {
  def apply[A >: Null](a: A) = new NullSafe(a)

  def Null: NullSafe[Null] = NullSafe(null)

  private val constNull: Any => Null = _ => null
}
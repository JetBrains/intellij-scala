package org.jetbrains.plugins.scala.lang.psi.uast.utils

import scala.language.implicitConversions

/**
  * Utils that help operating nullable values which come from outside
  * (e.g. from Java or Kotlin).
  * Imitate Kotlin null-handling operators.
  *
  * @note Should be avoided if possible
  */
object OptionExt {
  implicit class OptionOps[A](val o: Option[A]) extends AnyVal {
    /**
      * Imitates Kotlin `?.` safe call operator.
      * Unlike {{{Option.map}}} method result of applying a transforming function
      * can be null itself so it is wrapped into [[Option]] too.
      */
    def ?>[B](f: A => B): Option[B] = o.flatMap(v => Option(f(v)))

    // imitates Kotlin `?:` "Elvis" operator
    def ??[U >: A](f: => U): U = o.getOrElse(f)
  }

  /**
    * Implicitly wraps nullable value.
    */
  implicit class AutoOptionWrapper[A](val v: A) extends AnyVal {
    def ?>[B](f: A => B): Option[B] = Option(v) ?> f

    def ??[U >: A](f: => U): U = Option(v).getOrElse(f)
  }

  /**
    * Tries to implicitly unwrap [[Option]] to the underlying nullable value.
    * Should be used carefully:
    *  - may occur where you did not wanted it (e.g. it had been better if you used {{{OptionOps.??}}})
    *  - may not occur where you want it to do so (e.g. {{{val o: Object = new Option(null))}}})
    *
    *  That is why it has been separated from other module and should be imported separately.
    */
  object AutoUnwrap {
    implicit def unwrap[A >: Null](o: Option[A]): A = o.orNull
  }
}

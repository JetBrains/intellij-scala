package org.jetbrains.plugins.scala.util

import scala.annotation.implicitAmbiguous

/**
 * Scala hack to enforce in compile time check that a type parameter
 * with this bound is __not__ `Nothing`.
 * Consequently it may forbid omitting this parameter in method call, e.g.
 * {{{
 *   def get[T: NotNothing]: T = ???
 *
 *   def example {
 *     get[Int]     // ok
 *     get[Nothing] // wrong
 *     get          // wrong due to expansion in get[Nothing]
 *   }
 * }}}
 *
 * @tparam T type param which can not be `Nothing`
 */
sealed trait NotNothing[T]

private object NotNothing {
  implicit def good[T]: NotNothing[T] = null

  @implicitAmbiguous("Specify generic type other than Nothing")
  implicit def wrong1: NotNothing[Nothing] = null
  implicit def wrong2: NotNothing[Nothing] = null
}

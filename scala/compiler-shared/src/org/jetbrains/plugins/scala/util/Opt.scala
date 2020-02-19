package org.jetbrains.plugins.scala.util

object Opt {

  def fromOption[A](option: Option[A], error: => String): Opt[A] = option match {
    case Some(value) => Right(value)
    case None => Left(error)
  }

  def apply[A](value: A, error: => String): Opt[A] =
    fromOption(Option(value), error)

  def ?(condition: Boolean, error: => String): Opt[Unit] =
    if (condition) Right(()) else Left(error)
}

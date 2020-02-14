package org.jetbrains.plugins.scala.compiler.data.serialization

object extensions {

  implicit class SeqEitherExt[E, R](private val target: Seq[Either[E, R]]) extends AnyVal {
    def collectErrors: Either[Seq[E], Seq[R]] = {
      val (results, errors) = target.partition(_.isRight)
      if (errors.isEmpty) Right(results.map(_.right.get))
      else Left(errors.map(_.left.get))
    }
  }

  implicit class EitherExt[A, B](private val target: Either[A, B]) extends AnyVal {
    def lift: Either[Seq[A], B] = target.left.map(Seq(_))
  }
}
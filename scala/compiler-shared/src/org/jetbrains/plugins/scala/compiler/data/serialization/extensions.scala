package org.jetbrains.plugins.scala.compiler.data.serialization

object extensions {

  implicit class SeqEitherExt[E, R](private val target: Seq[Either[E, R]]) extends AnyVal {
    def collectErrors: Either[Seq[E], Seq[R]] = {
      val (results, errors) = target.partition(_.isRight)
      if (errors.isEmpty) Right(results.map(_.getRight))
      else Left(errors.map(_.getLeft))
    }
  }

  implicit class EitherExt[A, B](private val target: Either[A, B]) extends AnyVal {
    def lift: Either[Seq[A], B] = target.left.map(Seq(_))
    def getRight: B = target.getOrElse(throw new NoSuchElementException("Either.getRight on Left"))
    def getLeft: A = target.swap.getOrElse(throw new NoSuchElementException("Either.getLeft on Right"))
  }
}
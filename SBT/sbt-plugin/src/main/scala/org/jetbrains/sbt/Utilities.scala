package org.jetbrains.sbt

/**
 * @author Pavel Fatin
 */
object Utilities {
  implicit def seqToDistinct[T](xs: Seq[T]) = new {
    def distinctBy[A](f: T => A): Seq[T] = {
      val (_, ys) = xs.foldLeft((Set.empty[A], Vector.empty[T])) {
        case ((set, acc), x) =>
          val v = f(x)
          if (set.contains(v)) (set, acc) else (set + v, acc :+ x)
      }
      ys
    }
  }

  implicit def toRichBoolean(b: Boolean) = new {
    def option[A](a: => A): Option[A] = if(b) Some(a) else None

    def either[A, B](right: => B)(left: => A): Either[A, B] = if (b) Right(right) else Left(left)
  }
}

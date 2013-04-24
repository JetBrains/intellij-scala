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
}

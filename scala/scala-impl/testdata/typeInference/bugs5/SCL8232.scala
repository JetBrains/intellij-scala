object Moo {
  implicit class IdOps[V](self: V) {
    def left[A]: Either[V, A] = Left(self)

    def right[E]: Either[E, V] = Right(self)
  }

  implicit class EitherOps[E, A](self: Either[E, A]) {
    def flatMap[EE >: E, B](f: A => Either[EE, B]): Either[EE, B] = self match {
      case Left(e) => Left(e)
      case Right(v) => f(v)
    }

    def map2[EE >: E, B, C](that: Either[EE, B])(f: (A, B) => C): Either[EE, C] = {
      self flatMap { a =>
        that flatMap { b =>
          f(a, b).right
        }
      }
    }
  }

  implicit class ListOps[E, A](self: List[A]) {
    def traverse[B](f: A => Either[E, B]): Either[E, List[B]] = {
      (self :\ List.empty[B].right[E])((x, evs) => (f(x) map2 evs) (_ :: _))
    }
  }

  def id[A](v: A): A = v

  implicit class ListEitherOps[E, A](self: List[Either[E, A]]) {
    def sequence(es: List[Either[E, A]]): Either[E, List[A]] = /*start*/self.traverse(id)/*end*/ // Type mismatched
  }
}
//Either[E, List[A]]
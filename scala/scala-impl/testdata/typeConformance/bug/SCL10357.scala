trait T1[+X]
trait T2[+X] extends T1[X]

trait Process[+F[_], +O] {
  def ++[F2[x] >: F[x], O2 >: O](p2: Process[F2, O2]): Process[F2, O2]
}

object Test {
  val z: Process[T1, Unit] =
    /*caret*/(??? : Process[Nothing, Unit]) ++ (??? : Process[T1, Unit])
}
//true
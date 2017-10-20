object SCL8079 {
  trait F {
    type R
  }
  class U extends F {
    type R = Int
  }
  class R[T]
  class Q[T, E, C[_]] extends R[C[E]]
  class TQ[T <: F] extends Q[T, T#R, scala.Seq]
  def foo[U](rep: R[U]): String = ""

  def foo[U, C[_]](q: Q[_, U, C]): Int = 1

  val users = new TQ[U]

  /*start*/foo(users)/*end*/
}
//Int
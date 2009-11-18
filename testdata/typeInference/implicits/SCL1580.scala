trait Show[-A] {
  def show(a: A): List[Char]
}

sealed trait MA[M[_], A] {
  val v: A
}

implicit def ShowMA[A](a: Show[A]): MA[Show, A] = error("TODO")
var a: Show[Int] = null
/*start*/a.v/*end*/
//Int
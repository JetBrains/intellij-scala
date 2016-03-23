object SCL7085 {
  abstract class A[+T]
  case object B extends A[Nothing]
  case object C extends A[Nothing]

  case class U(options: Set[A[_]])

  private val b: Option[B.type] = Option(B)
  private val c: Option[C.type] = Option(C)

  def foo(x: Set[A[_]]): Int = 1
  def foo(x: Boolean): Boolean = false
  /*start*/foo(Set() ++ b ++ c ++ (if (true) Seq(B) else Set()))/*end*/
}
//Int
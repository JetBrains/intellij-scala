object SCL7100 {
  class Nil extends List
  class List
  val nl = new Nil
  trait C[T] {
    type Out
  }
  class P[L, R]
  def foo(c: C[Nil]): c.Out = sys.exit()

  implicit def p: P[Nil, Nil] = new P
  implicit def l2c[L <: List, R <: List](s: Seq[R])(implicit p: P[L, R]) = new C[R] {
    type Out = L
  }

  /*start*/foo(Seq(nl))/*end*/
}
//SCL7100.Nil
object SCL7388C {

  class P[T, S]
  class G[T]
  class B[S](p: PL[S, _]) extends P[S, S]
  class PL[T, S]
  def pl[T, S]: PL[T, S] = new PL
  def b[S](p: PL[S, _]): B[S] = null

  def a[T](p: P[_ <: Any, T]): G[T] = new G[T]
  val g : G[String] = a(/*start*/new B(new PL)/*end*/)
}
//SCL7388C.B[String]
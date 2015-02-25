object SCL8241 {
  class B[T]
  class X[M[_]]
  def foo[M[_]](implicit x: X[M]): X[M] = x
  implicit val x: X[B] = new X[B]
  implicit val x2: X[Option] = new X[Option]
  val aa: X[B] = /*start*/foo/*end*/
}
//SCL8241.X[SCL8241.B]
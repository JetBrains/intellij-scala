trait C {
  self: Z =>
  case class I(i: Int)
  case class F(j: Int) extends I(j)

  class T
  class U extends T

  object U {
    def unapply(u: T): Option[U] = None
  }
}

trait K {
  self : Z =>

  S(F(1))

  val x: U = null

  x match {
    case U(u) =>
      /* line: 26 */foo(u)
  }

  def foo(t: T) = 1
  def foo(u: Int) = 2

  case class S(a: I) {
  }


}

trait Z extends K with C {

}
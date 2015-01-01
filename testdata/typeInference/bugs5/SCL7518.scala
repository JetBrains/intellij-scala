object SCL7518 {
  abstract case class AImpl(s: String)

  class F {
    def copy(s: String) = 1
  }

  class G

  implicit def g2aimpl(g: G): AImpl = new AImpl("text") {}
  implicit def g2f(g: G): F = new F()

  val g = new G

  /*start*/g.copy("text")/*end*/
}
//Int
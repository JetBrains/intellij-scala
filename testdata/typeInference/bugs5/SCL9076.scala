object SCL9076 {
  trait Impl[A, Repr] {
    implicit def const(i: A): Repr

    def newVar(init: Repr): Repr = ???
  }
  object IntObj extends Impl[Int, IntObj] {
    def const(i: Int): IntObj = ???
  }
  trait IntObj

  def test(): Unit =
    IntObj.newVar(/*start*/0/*end*/)
}
//SCL9076.IntObj
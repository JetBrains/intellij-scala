// Notification message: null
class ImplicitParamatersUsed {
  object Z {
    class A
    implicit val a = new A
  }

  class G {
    def map(x: Int => Int)(implicit a: Z.A) = 123
  }

  def moo() {
    import Z._
    val b = new G
    for (i <- b) yield i
  }
}
/*
class ImplicitParamatersUsed {
  object Z {
    class A
    implicit val a = new A
  }

  class G {
    def map(x: Int => Int)(implicit a: Z.A) = 123
  }

  def moo() {
    import Z._
    val b = new G
    for (i <- b) yield i
  }
}
 */
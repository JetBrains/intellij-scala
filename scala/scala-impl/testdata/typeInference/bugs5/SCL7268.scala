object SCL7268 {
  class Low {
    implicit val x: B = new B
  }

  object B extends Low {
    implicit def foo(implicit s: String): B =  new B
  }

  class B
  class C {
    type Out
  }

  object C {
    implicit def g(implicit s: Boolean): C = new C { type Out = String}
  }

  implicit val s: String = "text"

  implicit def c(implicit b: B) = new C {type Out = Int}
  def foo(implicit c: C): c.Out = sys.exit()

  /*start*/foo/*end*/
}
//Int
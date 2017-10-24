class Unapply2 {
  class B {
    def _1 = "text"
    def _2 = Seq(1, 2, 3)
  }
  class A {
    val isEmpty: Boolean = false
    def get: B = new B
  }

  object Z {
    def unapply(s: String): A = new A
  }

  "text" match {
    case Z(s, l) =>
      /*start*/l/*end*/
  }
}
//Seq[Int]
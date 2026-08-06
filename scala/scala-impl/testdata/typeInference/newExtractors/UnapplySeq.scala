class UnapplySeq {
  class B {
    def _1 = "text"
    def _2 = Seq(1, 2, 3)
  }
  class A {
    val isEmpty: Boolean = false
    def get: B = new B
  }
  object Z {
    def unapplySeq(s: String): Option[B] = None
  }

  "text" match {
    case Z(s, l, z) =>
      /*start*/l/*end*/
  }
}
//Int
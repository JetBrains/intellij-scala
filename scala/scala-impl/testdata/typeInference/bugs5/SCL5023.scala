class test {

  case class C(a: Int) {
    def a(i: Int): Int = 1
  }

  def f(c: C) = /*start*/c.a/*end*/ //missing arguments for method a(Int)
}
//Int
trait Zoo[T] {
  object Moo {
    def foo(x: T): T = x
  }
  class P

  def goo: U
  type U
}

object A extends Zoo[Int] {
  val x: P = new P
  /*start*/Moo.foo(1)/*end*/
}
//Int
abstract class Test {
  def foo[T](z: {def x(p: Int): T}): T

  class O {
    def x(p: Int): Int = p
  }
  /*start*/foo(new O)/*end*/
}
//Int
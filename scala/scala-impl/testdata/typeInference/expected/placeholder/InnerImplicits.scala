object A {
  class S {
    def x: Array[Int] = Array.empty
  }
  def foo(f: S => Seq[Int]): Int = 1
  def foo(x: Int): Boolean = false

  /*start*/foo(_.x)/*end*/
}
//Int
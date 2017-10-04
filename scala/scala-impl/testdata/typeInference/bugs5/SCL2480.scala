object SCL2480 {
trait Bug[Repr] { self: Repr =>
  def foo(k : KB[Repr]) {
    /*start*/k(self)/*end*/
  }
}

trait KB[From] {
  def apply(from: From) = 1

  def apply(x: Int) = false
}
}
//Int
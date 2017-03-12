trait Unapply {
  def unapply(x: Int): Option[Int] = Some(x)
}

def foo(A: Any with Unapply) {
  22 match {
    case A(<caret>) =>
  }
}
//Int
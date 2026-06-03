object IO {
  def partial[R]: MyPartiallyApplied[R] = ???
  case class MyPartiallyApplied[R]() {
    def apply[E, A](x: A) = ???
  }

  IO.partial[<caret>] { ??? }
}
//TEXT: R, STRIKEOUT: false
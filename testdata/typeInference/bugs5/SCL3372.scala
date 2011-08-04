object A {
  def foo[S <: String] {}
  def qux[S <: CharSequence] {}

  def bar[T](f : { def foo[S <: T]; def qux[S <: T] }) : T = {
    /*start*/bar(A).toLowerCase/*end*/
    error("")
  }
}
//String
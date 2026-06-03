object ImplicitParameterOnlyFirstStep {
class A[T](val x: T)

object A {
  implicit val a: A[Int] = new A[Int](1)
}

object C {
  def foo[T]()(implicit a: A[T]) = a.x

  def first {
    foo()
  }

  def second {
    implicit val a: A[String] = new A[String]("")
    /*start*/foo()/*end*/
  }
}
}
//String
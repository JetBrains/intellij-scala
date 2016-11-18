trait D
trait B {
  this: D =>

  abstract class A[T1, T2]
  object A {
    implicit object Inner extends A[Int, String] {
    }
  }
}

trait C extends D with B {
  /*caret*/
  val a: A[Int, String] = A.Inner
}
//True
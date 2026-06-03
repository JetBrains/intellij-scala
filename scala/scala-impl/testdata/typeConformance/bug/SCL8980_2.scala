trait D
trait B {
  this: D =>

  abstract class A[T1, T2]
  object AObj {
    implicit object Inner extends B.this.A[Int, String] {
    }
  }
}

trait C extends D with B {
  /*caret*/
  val a: A[Int, String] = C.this.AObj.Inner
}
//True
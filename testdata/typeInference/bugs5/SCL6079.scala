abstract class A {
  class B[T]
  class C extends B[S]
  type S <: B[S]

  implicit def foo(x: Int)(implicit s: B[S]) = 1

  implicit val x: Int = 123

  implicit def goo(implicit i: Int): C = sys.exit()

  /*start*/foo(1)/*end*/

}
//Int
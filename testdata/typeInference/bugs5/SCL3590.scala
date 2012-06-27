object Test {
  class A
  class B
  class C[T]

  val x: Seq[C[_]] = /*start*/Seq(new C[A], new C[B])/*end*/
}
//Seq[Test.C[_ >: Test.B with Test.A <: Object]]
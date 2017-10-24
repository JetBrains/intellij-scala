object Test {
  class A
  class B
  class C[T]

  val x: Seq[C[_]] = /*start*/Seq(new C[A], new C[B])/*end*/
}
/*
Few variants:
Seq[Test.C[_ >: Test.B with Test.A <: Object]]
Seq[Test.C[_ >: Test.A with Test.B <: Object]]
 */
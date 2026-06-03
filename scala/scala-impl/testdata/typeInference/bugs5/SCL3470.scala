object SCL3470 {
  class A; class B; class C
  implicit def c2a(c: C): A = new A
  implicit def b2a(b: B): A = new A

  def foo[A](as: A*): Seq[A] = null
  def bar[A](a1: A, a2: A): Seq[A] = null

  val r1: Seq[A] = foo[A](new B)     // okay
  val r2: Seq[A] = foo(new A)        // okay
  val r3: Seq[A] = foo(new B)        // okay
  val r4: Seq[A] = bar(new B, new B) // okay

  val r5: Seq[A] = foo(new B, new B) // fail: Seq[B] doesn't conform to Seq[A]
  val r6: Seq[A] = foo(new B, new C) // fail: Seq[ScalaObject] doesn't conform to Seq[A]
  /*start*/(r1, r2, r3, r4, r5, r6)/*end*/
}
//(Seq[SCL3470.A], Seq[SCL3470.A], Seq[SCL3470.A], Seq[SCL3470.A], Seq[SCL3470.A], Seq[SCL3470.A])
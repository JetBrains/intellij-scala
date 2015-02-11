object SCL8261 {
  sealed trait Foo
  trait FooOps[A <: Foo] {
    def op: A
  }

  object Foo {
    implicit def toOps[A <: Foo](a: A): FooOps[A] = new FooOps[A] {
      override def op = a
    }
  }

  // Both versions compile
  // This works fine
  def concrete(a: Foo): Foo = a.op
  // IDEA says it cannot find op
  def generic[A <: Foo](a: A): A = /*start*/a.op/*end*/
}
//A
class U[T] {
  def foo[B](x: T => B) = 122
}

class A[T] {
  class F extends U[T] {
    override def foo[B](x : T => B) = 123
  }
}

class B[T] extends A[T] {
  class Z extends B.super.F {
    override def foo[B](x : T => B) = 124
  }
}

val b : B[Int]#Z = null
b.foo(i => /*start*/i/*end*/)
//Int
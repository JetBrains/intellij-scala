object SCL6628 {
  def foo(x: Int => Any) = 123
  def foo(x: Int) = 444

  case class A(x: Int)

  /* resolved: true */foo(A)

  class B(x: Int)
  object B {
    def apply(x: Int): B = new B(x)
  }

  /*resolved: false */foo(B)

  case class C[T](x: T)

  /* resolved: false */foo(C)

  List(1, 2, 3)./* applicable: false */map(C)
}
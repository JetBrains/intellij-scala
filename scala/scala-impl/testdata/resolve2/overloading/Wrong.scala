class A[T] {
  def goo(x: T) = 1

  def goo(x: List[T]) = 2
}

def foo[T]: A[T] = new A[T]

foo./* resolved: false */goo(List(1, 2))

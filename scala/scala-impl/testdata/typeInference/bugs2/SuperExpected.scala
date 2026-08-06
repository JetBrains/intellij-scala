class A[T]

def a[T]: A[T] = new A[T]

class K {
  def foo: A[Int] = new A[Int]
}

class S extends K {
  override def foo = a

  /*start*/foo/*end*/
}
//A[Int]
import collection.immutable.HashMap


class B[+T] {
  def foo[Z >: T](x: Z): (B[T], Z) = new B[T] -> x
}

/*start*/((new B).foo(1), (new HashMap).+((1, 2)))/*end*/
//((B[Nothing], Int), HashMap[Int, Int])
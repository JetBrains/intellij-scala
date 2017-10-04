class A {
  def foo(x: A): A = x

  def foo: A = new A
}

val a: A => A = /*start*/_.foo/*end*/
//A => A
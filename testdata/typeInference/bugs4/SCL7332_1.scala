class Foo[A, B](a: A)(b: B)(f: B => A)

val foo1 = new Foo(1)(identity[Int] _)( /*start*/f => f(2) /*end*/)

// (Int => Int) => Int
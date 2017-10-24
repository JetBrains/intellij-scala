class A

class B

implicit def a2b(a: A) = new B

trait M[X, Y]

implicit def MAA[A] = new M[A, A] {}
def foo[T, U](t: T)(implicit m: M[T, U]): U = error("")
val a = foo(new A) // inferred correctly as A
def b1: B = a // okay implicit conversion underlyings
def b2: B = /*start*/foo(new A)/*end*/
/*
Seq(a2b,
    any2ArrowAssoc,
    any2Ensuring,
    any2stringadd,
    any2stringfmt),
Some(a2b)
 */
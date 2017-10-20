class A
class B
implicit def a2b(a: A) = new B
trait M[X, Y]
implicit def MAA[A] = new M[A, A] {}
def foo[T, U](t: T)(implicit m: M[T, U]): U = error("")
def b2: B = /*start*/foo(new A)/*end*/
//B
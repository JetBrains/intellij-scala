class A[T] {
  def foo(x: String) = 0

  def foo(x: T): T = x
}
class B extends (A[Int] => Int)

val a = new B
def foao[T]: A[T] = new A[T]

a(/*start*/foao/*end*/)
//A[Int]
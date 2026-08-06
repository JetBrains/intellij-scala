class B {
  def foo(s: String) = 123
}

class A {
  def foo(a: A) = "text"
}
object A {
  implicit def fromB(b: B): A = new A
}

val b = new B
val a = new A

/*start*/b foo a/*end*/
//String
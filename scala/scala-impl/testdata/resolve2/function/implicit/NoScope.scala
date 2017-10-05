class B {
  def goo(s: String) = 123
}

class A {
  def foo(a: A) = "text"
}
object A {
  implicit def fromB(b: B): A = new A
}

val b = new B
val a = new A

b /* resolved: false */foo a
class A {
  def @@(other: A) = new B
}

class B

implicit def B2A(b: B) = new A
val a = new A
var b = new B
b /*line: 2, name: @@*/@@= a
trait Outer[U] {
  type T
  class A {
    def o(x: T) = x
    def u(x: U) = x
  }
}

object H extends Outer[String] {
  type T = Int
}

val a = new H.A

def foo(x: Int) = 1
def foo(x: String) = 2
/* line: 15 */foo(a.o(4))
/* line: 16 */foo(a.u(""))
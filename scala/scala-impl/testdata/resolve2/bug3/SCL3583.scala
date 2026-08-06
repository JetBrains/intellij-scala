class A

class B

implicit def a2b(a: A): B = new B
def wibble(f: Any => B) {}
def foo(a: Any): A = new A
/*resolved: true*/wibble(a => foo(a))
/*resolved: true*/wibble(foo)
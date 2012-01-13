class A
class B

class Foo(foo1: A, `2foo` : B)

def Bar(): Foo = {
  def foo(`1`: Int) = 2

  foo(/* name: `1` */`1` = 2)

  new Foo(
    foo1 = new A,
    /* name: `2foo` */`2foo` = new B
  )
}
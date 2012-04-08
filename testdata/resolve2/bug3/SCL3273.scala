class Foo[+T](val p1: T, val p2: T) {
  def bar[U](p1: T => U) {}

  def bar2(p1: T => Int) {}

  def bar3[U](p1: AnyVal => U) {}
}

new Foo(1, 1.0)./*resolved: true*/bar((value: AnyVal) => 0) // error
new Foo(1, 1.0)./*resolved: true*/bar2((value: AnyVal) => 0) // ok
new Foo(1, 1.0)./*resolved: true*/bar3((value: AnyVal) => 0) // ok
List(1, 1.0)./*resolved: true*/foreach((value: AnyVal) => 0) // error
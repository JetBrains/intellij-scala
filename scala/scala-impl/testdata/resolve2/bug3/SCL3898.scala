object Main {
  class C[T] {}
  class D[T] {}
  class E {}

  def foo[T, V <: T](c: C[T], d: D[V]) {print(1)}
  def foo[T](c : C[T], t: T) {print(2)}

  def bar {
    /* line: 6 */foo(new C[E], new D[E])
  }
}
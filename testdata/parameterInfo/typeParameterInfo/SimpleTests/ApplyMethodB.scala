class A {
  def apply[a] = 0
}

val a = new A
a[Int<caret>] // TODO
// a
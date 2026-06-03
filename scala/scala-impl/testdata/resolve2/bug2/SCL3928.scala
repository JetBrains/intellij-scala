class C {
  def f(x: Char => Unit) = 1
  def f(x: Int) = 2
}
val a: C = new C

def foo(x: Int) {}
a./* line: 2 */f(foo(_))
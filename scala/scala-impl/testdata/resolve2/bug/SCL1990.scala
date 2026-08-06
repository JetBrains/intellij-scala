class Outer {
  class Inner {}

  def foo(test: Inner) {}
  def foo(x: Int) {}
  def bar(test: Outer#Inner) {}
  def bar(x: Int) {}

  def use {
    val o1 = new Outer
    val o2 = new Outer

    o1./* line: 4 */foo(new o1.Inner)
    o1./* line: 6 */bar(new o2.Inner)
  }
}
()
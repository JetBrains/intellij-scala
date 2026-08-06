object Main {
  def foo()(implicit m: Manifest[Int]) = 0
  def foo(a: Int) = 1

  /* line: 2 */foo.toString

  def bar() = 0
  def bar(a: Int) = 0
  /* line: 7 */bar.toString
}
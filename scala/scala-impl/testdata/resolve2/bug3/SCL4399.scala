class Test {
  def foo(x: Int, y: Int*) = 2

  foo(/* line: 2 */x = 2)
}
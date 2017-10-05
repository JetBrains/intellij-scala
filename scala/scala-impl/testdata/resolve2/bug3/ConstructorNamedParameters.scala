class A {
  class B(val x: Int)

  def x = 1

  def moo() {
    new B(/* line: 2 */x = 1)
  }
}
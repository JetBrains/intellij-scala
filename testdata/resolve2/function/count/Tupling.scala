object Tupling {
  def foo(x: Any): Int = 1
  def foo(x: Int, y: Int, z: Int) = 2

  /* line: 2 */foo(1, 2)

  def goo(x: AnyRef): Int = 1
  def goo(x: Int, y: Int, z: Int) = 2

  /* line: 7 */goo(1, 2)

  def moo(x: (Int, Int)) = 1
  def moo(x: Int, y: Int, z: Int) = 2

  /* line: 12 */moo(1, 2)

  def soo[X, Y, Z](x: (X, Y, Z)) = 1
  def soo(z: Int) = 2

  /* line: 17 */soo(1, 2, 3)

  def joo(x: (Int, String)) = 1
  def joo(x: Int, y: Int) = 2

  /* resolved: false */joo(1, "")
}
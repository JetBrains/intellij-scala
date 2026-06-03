class SCL4039 {
  def foo(x: Int, y: String) = 1
  def foo(x: Int*)(y: String) = 2
  def goo(x: Int*)(y: String) = 2
  def goo(x: Int, y: String) = 1

  /* line: 2 */foo(1, "")
  /* line: 5 */goo(1, "")
}
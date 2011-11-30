object K {
  class A
  class B extends A
  def foo(x: B*) = 1
  def foo(x: A) = 2
  /* resolved: false */foo(new B)

  def fooa(x: String*) = 1
  def fooa(x: Any) = 2
  /* line: 8*/fooa("")

  def goo(x: Int*) = 1
  def goo(x: Int) = 2
  /* line: 13 */goo(1)
}
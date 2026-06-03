object Test extends Application {
  class A
  class B extends A
  def foo(x: A, y: B) = print(1)
  def foo(x: A, y: A) = print(2)

  /* line: 4 */foo(new B, new B)
}
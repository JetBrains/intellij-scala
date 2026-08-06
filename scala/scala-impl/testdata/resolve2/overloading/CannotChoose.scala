object Test extends Application {
  class A
  class B extends A
  def foo(x: A, y: B) = print(1)
  def foo(x: B, y: A) = print(2)

  /* resolved: false */foo(new B, new B)
}